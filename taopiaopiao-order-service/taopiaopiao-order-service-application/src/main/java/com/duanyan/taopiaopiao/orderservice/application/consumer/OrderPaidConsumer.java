package com.duanyan.taopiaopiao.orderservice.application.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderPaidMessage;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 支付成功事件消费者（订单服务）
 * <p>
 * 消费支付成功后的 ORDER_PAID 事务消息
 * 只负责更新订单状态
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_ORDER_PAID,
        consumerGroup = "order-service-order-paid-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderPaidConsumer implements RocketMQListener<OrderPaidMessage> {

    private final OrderMapper orderMapper;

    @Override
    public void onMessage(OrderPaidMessage message) {
        try {
            log.info("收到支付成功事件: orderNo={}, userId={}, sessionId={}",
                    message.getOrderNo(), message.getUserId(), message.getSessionId());

            // ORDER_PAID 只在支付成功后提交，这里不再重复查询支付状态
            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getOrderNo, message.getOrderNo())
            );

            if (order == null) {
                throw new RuntimeException("订单不存在: " + message.getOrderNo());
            }

            // 幂等：已支付则直接返回
            if (OrderStatus.PAID.getCode().equals(order.getStatus())) {
                log.info("订单已是支付状态，跳过: orderNo={}", message.getOrderNo());
                return;
            }

            // 不覆盖已经进入终态的订单，避免与取消/超时链路互相踩踏
            if (!OrderStatus.UNPAID.getCode().equals(order.getStatus())) {
                log.warn("订单状态不允许更新为已支付: orderNo={}, status={}",
                        message.getOrderNo(), order.getStatus());
                return;
            }

            int updated = orderMapper.markPaidIfUnpaid(message.getOrderNo(), OrderStatus.PAID.getCode());
            if (updated != 1) {
                Order latest = orderMapper.selectOne(
                        new LambdaQueryWrapper<Order>()
                                .eq(Order::getOrderNo, message.getOrderNo())
                );
                if (latest != null && OrderStatus.PAID.getCode().equals(latest.getStatus())) {
                    log.info("订单已由其他链路更新为支付状态，跳过: orderNo={}", message.getOrderNo());
                    return;
                }
                throw new RuntimeException("更新订单状态失败: " + message.getOrderNo());
            }

            log.info("订单支付成功，状态已更新: orderNo={}", message.getOrderNo());

        } catch (Exception e) {
            log.error("处理支付成功事件异常: orderNo={}", message.getOrderNo(), e);
            throw e; // 抛出异常，触发重试
        }
    }
}

package com.duanyan.taopiaopiao.orderservice.application.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单取消消息消费者（Order Service）
 * <p>
 * 功能：
 * - 监听订单取消消息
 * - 更新订单状态为 CANCELLED 或 TIMEOUT
 * - 处理幂等性（避免重复消费）
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_CANCEL_ORDER,
        consumerGroup = "order-service-cancel-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderCancelConsumer implements org.apache.rocketmq.spring.core.RocketMQListener<OrderCancelMessage> {

    private final OrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(OrderCancelMessage message) {
        try {
            log.info("收到订单取消消息: orderNo={}, reason={}", message.getOrderNo(), message.getReason());

            // 1. 幂等性校验：检查订单状态
            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getOrderNo, message.getOrderNo())
            );

            if (order == null) {
                log.warn("订单不存在，跳过处理: orderNo={}", message.getOrderNo());
                return;
            }

            // 2. 判断是否需要处理
            if (OrderStatus.PAID.getCode().equals(order.getStatus())) {
                log.info("订单已支付，不处理取消: orderNo={}", message.getOrderNo());
                return;
            }

            if (OrderStatus.CANCELLED.getCode().equals(order.getStatus())
                    || OrderStatus.TIMEOUT.getCode().equals(order.getStatus())) {
                log.info("订单已取消，跳过处理: orderNo={}, status={}",
                        message.getOrderNo(), order.getStatus());
                return;
            }

            // 3. 更新订单状态
            if ("TIMEOUT".equals(message.getReason())) {
                order.setStatus(OrderStatus.TIMEOUT.getCode());
            } else {
                order.setStatus(OrderStatus.CANCELLED.getCode());
            }
            order.setCancelTime(LocalDateTime.now());
            order.setUpdatedAt(null); // 让 MyBatis-Plus 自动填充

            orderMapper.updateById(order);

            log.info("订单取消成功: orderNo={}, reason={}, status={}",
                    message.getOrderNo(), message.getReason(), order.getStatus());

        } catch (Exception e) {
            log.error("处理订单取消消息异常: orderNo={}", message.getOrderNo(), e);
            throw e; // 抛出异常，触发重试
        }
    }
}

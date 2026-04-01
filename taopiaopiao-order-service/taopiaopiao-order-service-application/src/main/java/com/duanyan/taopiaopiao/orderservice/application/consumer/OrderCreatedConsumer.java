package com.duanyan.taopiaopiao.orderservice.application.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCreatedMessage;
import com.duanyan.taopiaopiao.orderservice.application.client.PaymentClient;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentQueryResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentResult;
import com.duanyan.taopiaopiao.orderservice.application.client.SeckillInternalClient;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderStatus;
import com.duanyan.taopiaopiao.common.response.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单创建消息消费者（订单服务）
 * <p>
 * 消费 ORDER_CREATED 事务消息
 * 在支付成功后更新订单状态
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_ORDER_CREATED,
        consumerGroup = "order-service-order-created-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderCreatedConsumer implements RocketMQListener<OrderCreatedMessage> {

    private final PaymentClient paymentClient;
    private final SeckillInternalClient seckillInternalClient;
    private final com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper orderMapper;

    @Override
    public void onMessage(OrderCreatedMessage message) {
        try {
            log.info("收到订单创建消息: orderNo={}, userId={}, sessionId={}",
                    message.getOrderNo(), message.getUserId(), message.getSessionId());

            // 1. 查询支付系统获取真实支付状态
            PaymentResult<PaymentQueryResponse> result = paymentClient.queryPayment(message.getOrderNo());

            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("查询支付系统失败，稍后重试: orderNo={}", message.getOrderNo());
                throw new RuntimeException("查询支付系统失败");
            }

            PaymentQueryResponse payment = result.getData();

            // 2. 如果未支付，不处理（等待支付成功后重新消费）
            if (payment.isPending()) {
                log.info("订单仍待支付，跳过处理: orderNo={}", message.getOrderNo());
                return;
            }

            // 3. 查询订单
            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getOrderNo, message.getOrderNo())
            );

            if (order == null) {
                log.warn("订单不存在: orderNo={}", message.getOrderNo());
                return;
            }

            // 4. 如果已处理，跳过
            if (OrderStatus.PAID.getCode().equals(order.getStatus())) {
                log.info("订单已是支付状态，跳过: orderNo={}", message.getOrderNo());
                return;
            }

            // 5. 如果支付失败或已取消，更新订单状态
            if ("FAILED".equals(payment.getStatus()) || "CANCELLED".equals(payment.getStatus())) {
                order.setStatus(OrderStatus.CANCELLED.getCode());
                order.setCancelTime(LocalDateTime.now());
                orderMapper.updateById(order);
                log.info("订单支付失败/取消，更新状态: orderNo={}, status={}", message.getOrderNo(), payment.getStatus());
                return;
            }

            // 6. 支付成功，更新订单状态
            if (payment.isSuccess()) {
                // Redis 确认购买（状态 1 → 2）
                List<String> seatIds = message.getSeatIds();
                Result<Integer> markResult = seckillInternalClient.markSeatLocksPaid(
                        message.getOrderNo(),
                        message.getSessionId(),
                        message.getUserId(),
                        seatIds
                );

                if (markResult == null || !markResult.isSuccess()) {
                    log.error("确认购买失败，订单状态不更新: orderNo={}", message.getOrderNo());
                    throw new RuntimeException("确认购买失败");
                }

                // 更新订单状态
                order.setStatus(OrderStatus.PAID.getCode());
                order.setPayTime(payment.getPaidAt() != null ? payment.getPaidAt() : LocalDateTime.now());
                order.setUpdatedAt(null);
                orderMapper.updateById(order);

                log.info("订单支付成功，状态已更新: orderNo={}", message.getOrderNo());
            }

        } catch (Exception e) {
            log.error("处理订单创建消息异常: orderNo={}", message.getOrderNo(), e);
            throw e; // 抛出异常，触发重试
        }
    }
}

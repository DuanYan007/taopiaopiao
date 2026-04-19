package com.duanyan.taopiaopiao.orderservice.application.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.LockAcceptedMessage;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import com.duanyan.taopiaopiao.orderservice.api.dto.FormalOrderCreateRequest;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.application.producer.OrderCancelProducer;
import com.duanyan.taopiaopiao.orderservice.application.producer.OrderCreatedInternalProducer;
import com.duanyan.taopiaopiao.orderservice.application.producer.OrderTransactionProducer;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.common.mq.message.OrderCreatedInternalMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_LOCK_ACCEPTED,
        consumerGroup = "order-service-lock-accepted-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class LockAcceptedConsumer implements RocketMQListener<LockAcceptedMessage> {

    private final OrderMapper orderMapper;
    private final OrderTransactionProducer orderTransactionProducer;
    private final OrderCancelProducer orderCancelProducer;
    private final OrderCreatedInternalProducer orderCreatedInternalProducer;

    @Override
    public void onMessage(LockAcceptedMessage message) {
        try {
            log.info("收到锁座受理消息: orderNo={}, lockId={}, userId={}",
                    message.getOrderNo(), message.getLockId(), message.getUserId());

            Order existing = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getOrderNo, message.getOrderNo())
            );
            if (existing != null) {
                publishOrderCreated(existing);
                log.info("正式订单已存在，跳过重复创建: orderNo={}", message.getOrderNo());
                return;
            }

            if (message.getExpireTime() != null && message.getExpireTime().isBefore(LocalDateTime.now())) {
                orderCancelProducer.sendCancelMessage(OrderCancelMessage.builder()
                        .orderNo(message.getOrderNo())
                        .userId(message.getUserId())
                        .lockId(message.getLockId())
                        .sessionId(message.getSessionId())
                        .seatIds(message.getSeatIds())
                        .reason("TIMEOUT")
                        .build());
                log.warn("锁单已过期，直接走超时释放: orderNo={}", message.getOrderNo());
                return;
            }

            FormalOrderCreateRequest request = FormalOrderCreateRequest.builder()
                    .userId(message.getUserId())
                    .sessionId(message.getSessionId())
                    .lockId(message.getLockId())
                    .eventId(message.getEventId())
                    .seatIds(message.getSeatIds())
                    .seatCount(message.getSeatCount())
                    .unitPrice(message.getUnitPrice())
                    .totalAmount(message.getTotalAmount())
                    .expireSeconds(resolveExpireSeconds(message.getExpireTime()))
                    .build();

            boolean sent = orderTransactionProducer.sendCreateOrderTransaction(message.getOrderNo(), request);
            if (!sent) {
                throw new RuntimeException("发送建单事务消息失败");
            }

            log.info("异步建单事务消息已发送: orderNo={}", message.getOrderNo());
        } catch (Exception e) {
            log.error("处理锁座受理消息异常: orderNo={}", message.getOrderNo(), e);
            throw e;
        }
    }

    private int resolveExpireSeconds(LocalDateTime expireTime) {
        if (expireTime == null) {
            return 1;
        }
        return (int) Math.max(1L, Duration.between(LocalDateTime.now(), expireTime).getSeconds());
    }

    private void publishOrderCreated(Order order) {
        orderCreatedInternalProducer.send(OrderCreatedInternalMessage.builder()
                .orderNo(order.getOrderNo())
                .lockId(order.getLockId())
                .userId(order.getUserId())
                .sessionId(order.getSessionId())
                .createdAt(order.getCreatedAt())
                .build());
    }
}

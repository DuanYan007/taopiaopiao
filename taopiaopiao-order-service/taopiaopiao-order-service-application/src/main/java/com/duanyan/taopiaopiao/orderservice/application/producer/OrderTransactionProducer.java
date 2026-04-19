package com.duanyan.taopiaopiao.orderservice.application.producer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderPaidMessage;
import com.duanyan.taopiaopiao.orderservice.api.dto.FormalOrderCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单事务消息生产者
 * <p>
 * 发送订单支付链路所需的 RocketMQ 消息。
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTransactionProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送建单事务半消息。
     * <p>
     * 该半消息最终仍使用 `ORDER_PAID` 标签，由事务监听器完成本地建单，并在支付确认后再提交给下游。
     *
     * @param orderNo 订单号
     * @param request 正式订单创建参数
     * @return 是否发送成功
     */
    public boolean sendCreateOrderTransaction(String orderNo, FormalOrderCreateRequest request) {
        String destination = MqTopic.ORDER_TOPIC + ":" + MqTopic.TAG_ORDER_PAID;

        try {
            // 构建支付成功事件消息，由事务监听器决定是否最终提交
            OrderPaidMessage message = OrderPaidMessage.builder()
                    .orderNo(orderNo)
                    .userId(request.getUserId())
                    .lockId(request.getLockId())
                    .sessionId(request.getSessionId())
                    .eventId(request.getEventId())
                    .seatIds(request.getSeatIds())
                    .seatCount(request.getSeatCount())
                    .unitPrice(request.getUnitPrice())
                    .totalAmount(request.getTotalAmount())
                    .createdAt(LocalDateTime.now())
                    .expireTime(LocalDateTime.now().plusSeconds(request.getExpireSeconds()))
                    .build();

            // 构建 Spring Message，使用 orderNo 作为 key
            Message<OrderPaidMessage> msg = MessageBuilder.withPayload(message)
                    .setHeader("RocketMQMessageKeys", orderNo)
                    .build();

            // message 作为 arg 参数传递给 executeLocalTransaction
            rocketMQTemplate.sendMessageInTransaction(destination, msg, message);

            log.info("发送建单事务半消息成功: orderNo={}", orderNo);
            return true;

        } catch (Exception e) {
            log.error("发送建单事务半消息失败: orderNo={}", orderNo, e);
            return false;
        }
    }

    /**
     * 当超时检查发现订单已支付时，补发普通 ORDER_PAID 消息给下游。
     */
    public void sendOrderPaidEvent(OrderPaidMessage message) {
        Message<OrderPaidMessage> mqMessage = MessageBuilder.withPayload(message)
                .setHeader("RocketMQMessageKeys", message.getOrderNo())
                .build();

        rocketMQTemplate.syncSend(
                MqTopic.ORDER_TOPIC + ":" + MqTopic.TAG_ORDER_PAID,
                mqMessage
        );

        log.info("发送订单支付成功普通消息: orderNo={}", message.getOrderNo());
    }
}

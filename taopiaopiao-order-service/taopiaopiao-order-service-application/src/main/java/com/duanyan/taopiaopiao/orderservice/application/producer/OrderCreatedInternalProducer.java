package com.duanyan.taopiaopiao.orderservice.application.producer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCreatedInternalMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedInternalProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void send(OrderCreatedInternalMessage message) {
        Message<OrderCreatedInternalMessage> mqMessage = MessageBuilder.withPayload(message)
                .setHeader("RocketMQMessageKeys", message.getOrderNo())
                .build();

        rocketMQTemplate.syncSend(
                MqTopic.ORDER_TOPIC + ":" + MqTopic.TAG_ORDER_CREATED_INTERNAL,
                mqMessage
        );

        log.info("发送内部订单创建完成消息: orderNo={}, lockId={}", message.getOrderNo(), message.getLockId());
    }
}

package com.duanyan.taopiaopiao.seckillservice.application.producer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.LockAcceptedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LockAcceptedProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void send(LockAcceptedMessage message) {
        Message<LockAcceptedMessage> mqMessage = MessageBuilder.withPayload(message)
                .setHeader("RocketMQMessageKeys", message.getOrderNo())
                .build();

        rocketMQTemplate.syncSend(MqTopic.ORDER_TOPIC + ":" + MqTopic.TAG_LOCK_ACCEPTED, mqMessage);
        log.info("发送锁座受理消息成功: orderNo={}, lockId={}", message.getOrderNo(), message.getLockId());
    }
}

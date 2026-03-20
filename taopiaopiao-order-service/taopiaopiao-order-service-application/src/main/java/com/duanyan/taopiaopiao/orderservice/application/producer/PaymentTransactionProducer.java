package com.duanyan.taopiaopiao.orderservice.application.producer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.PaymentSuccessMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 支付事务消息生产者
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTransactionProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送支付成功事务消息
     *
     * @param message 消息内容
     */
    public void sendPaymentSuccessMessage(PaymentSuccessMessage message) {
        Message<PaymentSuccessMessage> mqMessage = MessageBuilder.withPayload(message).build();

        rocketMQTemplate.sendMessageInTransaction(
                MqTopic.PAYMENT_TOPIC + ":" + MqTopic.TAG_PAY_SUCCESS,
                mqMessage,
                message.getOrderNo()
        );

        log.info("发送支付成功事务消息: orderNo={}", message.getOrderNo());
    }
}

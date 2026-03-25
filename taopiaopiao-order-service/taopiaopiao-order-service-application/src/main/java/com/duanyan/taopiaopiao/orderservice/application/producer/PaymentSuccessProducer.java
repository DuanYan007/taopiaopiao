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
 * 支付成功消息生产者
 * <p>
 * 注意：本地事务已在 OrderService.pay() 中完成（订单状态更新、Redis 更新）
 * 此处使用普通消息异步通知 SessionService 更新 seats 表
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送支付成功消息（普通同步消息）
     * <p>
     * 发送时机：本地事务成功后
     * 消费者：SessionService 更新 seats 表状态为 sold
     *
     * @param message 消息内容
     */
    public void sendPaymentSuccessMessage(PaymentSuccessMessage message) {
        Message<PaymentSuccessMessage> mqMessage = MessageBuilder.withPayload(message).build();

        // 使用订单号作为消息 Key，用于幂等性控制
        rocketMQTemplate.syncSend(
                MqTopic.PAYMENT_TOPIC + ":" + MqTopic.TAG_PAY_SUCCESS,
                mqMessage,
                3000  // 3秒超时
        );

        log.info("发送支付成功消息成功: orderNo={}, seatIds={}", message.getOrderNo(), message.getSeatIds());
    }
}

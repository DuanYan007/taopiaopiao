package com.duanyan.taopiaopiao.sessionservice.application.consumer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.PaymentSuccessMessage;
import com.duanyan.taopiaopiao.sessionservice.application.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 支付成功消息消费者
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_TOPIC,
        selectorExpression = MqTopic.TAG_PAY_SUCCESS,
        consumerGroup = MqTopic.PAYMENT_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class PaymentSuccessConsumer implements org.apache.rocketmq.spring.core.RocketMQListener<PaymentSuccessMessage> {

    private final SeatService seatService;

    @Override
    public void onMessage(PaymentSuccessMessage message) {
        try {
            log.info("收到支付成功消息: orderNo={}", message.getOrderNo());

            // 幂等性校验：检查是否已处理
            if (seatService.isSeatsMarkedSold(message.getOrderNo())) {
                log.info("座位已标记为 sold，跳过处理: orderNo={}", message.getOrderNo());
                return;
            }

            // 标记座位为已售出
            seatService.markSeatsSold(message);

            log.info("处理支付成功消息成功: orderNo={}", message.getOrderNo());

        } catch (Exception e) {
            log.error("处理支付成功消息异常: orderNo={}", message.getOrderNo(), e);
            throw e;  // 抛出异常，触发重试
        }
    }
}

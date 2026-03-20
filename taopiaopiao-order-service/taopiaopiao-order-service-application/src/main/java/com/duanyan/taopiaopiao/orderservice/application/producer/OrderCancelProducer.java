package com.duanyan.taopiaopiao.orderservice.application.producer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 订单取消消息生产者
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送取消订单消息
     *
     * @param message 消息内容
     */
    public void sendCancelMessage(OrderCancelMessage message) {
        Message<OrderCancelMessage> mqMessage = MessageBuilder.withPayload(message).build();

        rocketMQTemplate.syncSend(
                MqTopic.ORDER_TOPIC + ":" + MqTopic.TAG_CANCEL_ORDER,
                mqMessage
        );

        log.info("发送订单取消消息: orderNo={}, reason={}", message.getOrderNo(), message.getReason());
    }

    /**
     * 发送延时取消消息（超时取消）
     *
     * @param message   消息内容
     * @param delayLevel 延时等级（1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m, 8=4m, 9=5m, 10=6m, 11=7m, 12=8m, 13=9m, 14=10m, 15=15m, 16=20m）
     */
    public void sendDelayCancelMessage(OrderCancelMessage message, int delayMinutes) {
        Message<OrderCancelMessage> mqMessage = MessageBuilder.withPayload(message).build();

        // RocketMQ 延时等级 16 对应 15 分钟
        int delayLevel = 16;
        rocketMQTemplate.syncSend(
                MqTopic.ORDER_TOPIC + ":" + MqTopic.TAG_CANCEL_ORDER,
                mqMessage,
                3000,
                delayLevel
        );

        log.info("发送延时取消消息: orderNo={}, delayMinutes={}", message.getOrderNo(), delayMinutes);
    }
}

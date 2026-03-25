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
     * RocketMQ 默认延时等级对照表
     * <pre>
     * 等级 1  = 1秒
     * 等级 2  = 5秒
     * 等级 3  = 10秒
     * 等级 4  = 30秒
     * 等级 5  = 1分钟
     * 等级 6  = 2分钟
     * 等级 7  = 3分钟
     * 等级 8  = 4分钟
     * 等级 9  = 5分钟
     * 等级 10 = 6分钟
     * 等级 11 = 7分钟
     * 等级 12 = 8分钟
     * 等级 13 = 9分钟
     * 等级 14 = 10分钟
     * 等级 15 = 15分钟
     * 等级 16 = 20分钟
     * </pre>
     */

    /**
     * 发送延时取消消息（超时取消）
     *
     * @param message      消息内容
     * @param delayMinutes 延时分钟数（当前仅支持 15 分钟）
     */
    public void sendDelayCancelMessage(OrderCancelMessage message, int delayMinutes) {
        Message<OrderCancelMessage> mqMessage = MessageBuilder.withPayload(message).build();

        // 订单超时时间为 5 分钟，使用 RocketMQ 延时等级 9
        // 注意：如果需要其他延时时间，需要在 Broker 配置文件中自定义延时等级
        int delayLevel = 5;  // 5 分钟
        rocketMQTemplate.syncSend(
                MqTopic.ORDER_TOPIC + ":" + MqTopic.TAG_CANCEL_ORDER,
                mqMessage,
                3000,
                delayLevel
        );

        log.info("发送延时取消消息: orderNo={}, delayMinutes={}, delayLevel={}",
                message.getOrderNo(), delayMinutes, delayLevel);
    }
}

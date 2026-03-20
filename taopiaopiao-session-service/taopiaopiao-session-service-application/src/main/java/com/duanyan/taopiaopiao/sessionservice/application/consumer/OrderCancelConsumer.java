package com.duanyan.taopiaopiao.sessionservice.application.consumer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 订单取消消息消费者
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_CANCEL_ORDER,
        consumerGroup = MqTopic.ORDER_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderCancelConsumer implements org.apache.rocketmq.spring.core.RocketMQListener<OrderCancelMessage> {

    /**
     * 处理订单取消消息
     * 注意：座位的释放由 SeckillService 的内部接口处理
     * 此消费者主要用于记录日志或后续扩展
     *
     * @param message 取消消息
     */
    @Override
    public void onMessage(OrderCancelMessage message) {
        try {
            log.info("收到订单取消消息: orderNo={}, reason={}", message.getOrderNo(), message.getReason());

            // 座位释放逻辑由 SeckillService 内部接口处理
            // 此处可添加其他业务逻辑，如发送通知等

            log.info("处理订单取消消息成功: orderNo={}", message.getOrderNo());

        } catch (Exception e) {
            log.error("处理订单取消消息异常: orderNo={}", message.getOrderNo(), e);
            throw e;  // 抛出异常，触发重试
        }
    }
}

package com.duanyan.taopiaopiao.sessionservice.application.consumer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单取消消息消费者（Session Service）
 * <p>
 * 注意：
 * - 座位的释放由 SeckillService 的 OrderCancelConsumer 处理
 * - 此消费者仅用于日志记录和监控
 * - SessionService 中不需要额外的取消逻辑，因为 seats 表在支付时才更新
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_CANCEL_ORDER,
        consumerGroup = "session-service-cancel-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderCancelConsumer implements RocketMQListener<OrderCancelMessage> {

    @Override
    public void onMessage(OrderCancelMessage message) {
        // 仅记录日志，座位释放由 SeckillService 处理
        log.info("收到订单取消消息（仅记录）: orderNo={}, reason={}, seatIds={}",
                message.getOrderNo(), message.getReason(), message.getSeatIds());
    }
}

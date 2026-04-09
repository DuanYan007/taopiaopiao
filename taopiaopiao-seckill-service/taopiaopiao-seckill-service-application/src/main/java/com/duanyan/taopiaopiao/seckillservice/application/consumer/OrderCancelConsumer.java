package com.duanyan.taopiaopiao.seckillservice.application.consumer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import com.duanyan.taopiaopiao.seckillservice.application.service.impl.SeckillServiceImpl;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单取消消息消费者
 * <p>
 * 消费最终确认后的取消事件，负责释放 Redis 座位并更新 `seat_locks` 状态。
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
        consumerGroup = "seckill-service-cancel-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderCancelConsumer implements RocketMQListener<OrderCancelMessage> {

    private final SeckillServiceImpl seckillService;

    @Override
    public void onMessage(OrderCancelMessage message) {
        try {
            log.info("收到订单取消消息: orderNo={}, reason={}, seatIds={}",
                    message.getOrderNo(), message.getReason(), message.getSeatIds());

            // 调用 SeckillService 释放座位
            LockStatus releaseStatus = "TIMEOUT".equals(message.getReason())
                    ? LockStatus.EXPIRED
                    : LockStatus.RELEASED;
            seckillService.releaseSeats(message.getSessionId(), message.getUserId(),
                    message.getLockId(), message.getSeatIds(), releaseStatus);

            log.info("处理订单取消消息成功: orderNo={}, reason={}", message.getOrderNo(), message.getReason());

        } catch (Exception e) {
            log.error("处理订单取消消息异常: orderNo={}", message.getOrderNo(), e);
            throw e;  // 抛出异常，触发重试
        }
    }
}

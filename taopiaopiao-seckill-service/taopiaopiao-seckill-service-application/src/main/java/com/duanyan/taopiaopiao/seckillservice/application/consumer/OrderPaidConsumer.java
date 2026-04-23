package com.duanyan.taopiaopiao.seckillservice.application.consumer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderPaidMessage;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.seckillservice.application.service.impl.SeckillServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 支付成功事件消费者（秒杀服务）
 *
 * 负责 Redis 座位确认购买并推进 Redis 锁单终态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_ORDER_PAID,
        consumerGroup = "seckill-service-order-paid-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderPaidConsumer implements RocketMQListener<OrderPaidMessage> {

    private final RedisService redisService;
    private final SeckillServiceImpl seckillService;

    @Override
    public void onMessage(OrderPaidMessage message) {
        try {
            log.info("收到支付成功消息: orderNo={}, userId={}, sessionId={}",
                    message.getOrderNo(), message.getUserId(), message.getSessionId());

            List<String> seatIds = message.getSeatIds();
            boolean confirmed = redisService.confirmPurchase(
                    message.getSessionId(),
                    message.getUserId(),
                    message.getLockId(),
                    seatIds
            );
            if (!confirmed) {
                throw new RuntimeException("Redis 确认购买失败: " + message.getOrderNo());
            }

            seckillService.markLockOrderPaid(message.getOrderNo());
            log.info("支付成功副作用处理完成: orderNo={}, seatCount={}", message.getOrderNo(), seatIds.size());
        } catch (Exception e) {
            log.error("处理支付成功消息异常: orderNo={}", message.getOrderNo(), e);
            throw e;
        }
    }
}

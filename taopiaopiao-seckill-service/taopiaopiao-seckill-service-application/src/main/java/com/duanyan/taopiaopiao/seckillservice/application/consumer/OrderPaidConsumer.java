package com.duanyan.taopiaopiao.seckillservice.application.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderPaidMessage;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.seckillservice.application.mapper.SeatLockMapper;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.SeatLock;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockStatus;
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
 * 负责：
 * 1. Redis 锁座状态确认购买（1 -> 2）
 * 2. seat_locks 标记为已支付
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
    private final SeatLockMapper seatLockMapper;

    @Override
    public void onMessage(OrderPaidMessage message) {
        try {
            log.info("收到支付成功消息: orderNo={}, userId={}, sessionId={}",
                    message.getOrderNo(), message.getUserId(), message.getSessionId());

            List<String> seatIds = message.getSeatIds();
            Long paidCount = seatLockMapper.selectCount(
                    new LambdaQueryWrapper<SeatLock>()
                            .eq(SeatLock::getSessionId, message.getSessionId())
                            .eq(SeatLock::getUserId, message.getUserId())
                            .in(SeatLock::getSeatId, seatIds)
                            .eq(SeatLock::getStatus, LockStatus.PAID.getCode())
                            .eq(SeatLock::getOrderNo, message.getOrderNo())
            );
            if (paidCount != null && paidCount == seatIds.size()) {
                log.info("seat_locks 已全部支付，跳过: orderNo={}", message.getOrderNo());
                return;
            }

            boolean confirmed = redisService.confirmPurchase(
                    message.getSessionId(),
                    message.getUserId(),
                    seatIds
            );
            if (!confirmed) {
                throw new RuntimeException("Redis 确认购买失败: " + message.getOrderNo());
            }

            for (String seatId : seatIds) {
                seatLockMapper.markAsPaid(message.getSessionId(), message.getUserId(), seatId, message.getOrderNo());
            }

            Long finalPaidCount = seatLockMapper.selectCount(
                    new LambdaQueryWrapper<SeatLock>()
                            .eq(SeatLock::getSessionId, message.getSessionId())
                            .eq(SeatLock::getUserId, message.getUserId())
                            .in(SeatLock::getSeatId, seatIds)
                            .eq(SeatLock::getStatus, LockStatus.PAID.getCode())
                            .eq(SeatLock::getOrderNo, message.getOrderNo())
            );
            if (finalPaidCount == null || finalPaidCount != seatIds.size()) {
                throw new RuntimeException("seat_locks 未全部标记为已支付: " + message.getOrderNo());
            }

            log.info("支付成功副作用处理完成: orderNo={}, seatCount={}", message.getOrderNo(), seatIds.size());
        } catch (Exception e) {
            log.error("处理支付成功消息异常: orderNo={}", message.getOrderNo(), e);
            throw e;
        }
    }
}

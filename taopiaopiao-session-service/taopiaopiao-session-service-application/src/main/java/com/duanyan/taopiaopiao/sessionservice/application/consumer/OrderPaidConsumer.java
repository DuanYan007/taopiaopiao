package com.duanyan.taopiaopiao.sessionservice.application.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderPaidMessage;
import com.duanyan.taopiaopiao.sessionservice.application.mapper.SeatMapper;
import com.duanyan.taopiaopiao.sessionservice.domain.entity.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 支付成功事件消费者（场次服务）
 * <p>
 * 消费支付成功后的 ORDER_PAID 事务消息
 * 只负责更新数据库座位状态
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_ORDER_PAID,
        consumerGroup = "session-service-order-paid-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderPaidConsumer implements RocketMQListener<OrderPaidMessage> {

    private final SeatMapper seatMapper;

    @Override
    public void onMessage(OrderPaidMessage message) {
        try {
            log.info("收到支付成功事件: orderNo={}, userId={}, sessionId={}",
                    message.getOrderNo(), message.getUserId(), message.getSessionId());

            List<Long> seatIds = message.getSeatIds().stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            Long soldCount = seatMapper.selectCount(
                    new LambdaQueryWrapper<Seat>()
                            .eq(Seat::getSessionId, message.getSessionId())
                            .in(Seat::getId, seatIds)
                            .eq(Seat::getStatus, "sold")
                            .eq(Seat::getOrderNo, message.getOrderNo())
            );
            if (soldCount != null && soldCount == seatIds.size()) {
                log.info("座位已处理，跳过: orderNo={}, sessionId={}", message.getOrderNo(), message.getSessionId());
                return;
            }

            int updated = seatMapper.markSeatsSold(message.getSessionId(), seatIds, message.getOrderNo());
            log.info("更新数据库座位状态为已售出: orderNo={}, sessionId={}, count={}",
                    message.getOrderNo(), message.getSessionId(), updated);

            Long finalSoldCount = seatMapper.selectCount(
                    new LambdaQueryWrapper<Seat>()
                            .eq(Seat::getSessionId, message.getSessionId())
                            .in(Seat::getId, seatIds)
                            .eq(Seat::getStatus, "sold")
                            .eq(Seat::getOrderNo, message.getOrderNo())
            );
            if (finalSoldCount == null || finalSoldCount != seatIds.size()) {
                throw new RuntimeException("座位售出状态未全部更新: " + message.getOrderNo());
            }

        } catch (Exception e) {
            log.error("处理支付成功事件异常: orderNo={}", message.getOrderNo(), e);
            throw e; // 抛出异常，触发重试
        }
    }
}

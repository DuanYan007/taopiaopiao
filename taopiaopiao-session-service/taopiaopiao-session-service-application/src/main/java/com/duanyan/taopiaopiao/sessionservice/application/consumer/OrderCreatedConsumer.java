package com.duanyan.taopiaopiao.sessionservice.application.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCreatedMessage;
import com.duanyan.taopiaopiao.sessionservice.application.client.PaymentClient;
import com.duanyan.taopiaopiao.sessionservice.application.client.dto.PaymentQueryResponse;
import com.duanyan.taopiaopiao.sessionservice.application.client.dto.PaymentResult;
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

/**
 * 订单创建消息消费者（场次服务）
 * <p>
 * 消费 ORDER_CREATED 事务消息
 * 支付成功后更新数据库座位状态
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_ORDER_CREATED,
        consumerGroup = "session-service-order-created-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderCreatedConsumer implements RocketMQListener<OrderCreatedMessage> {

    private final PaymentClient paymentClient;
    private final SeatMapper seatMapper;

    @Override
    public void onMessage(OrderCreatedMessage message) {
        try {
            log.info("收到订单创建消息: orderNo={}, userId={}, sessionId={}",
                    message.getOrderNo(), message.getUserId(), message.getSessionId());

            // 1. 查询支付系统获取真实支付状态
            PaymentResult<PaymentQueryResponse> result = paymentClient.queryPayment(message.getOrderNo());

            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("查询支付系统失败，稍后重试: orderNo={}", message.getOrderNo());
                throw new RuntimeException("查询支付系统失败");
            }

            PaymentQueryResponse payment = result.getData();

            // 2. 如果未支付，不处理（等待支付成功后重新消费）
            if (payment.isPending()) {
                log.info("订单仍待支付，跳过处理: orderNo={}", message.getOrderNo());
                return;
            }

            // 3. 如果支付失败或已取消，不处理
            if (!payment.isSuccess()) {
                log.info("订单未成功支付，跳过: orderNo={}, status={}", message.getOrderNo(), payment.getStatus());
                return;
            }

            // 4. 支付成功，更新数据库座位状态
            // 先检查是否已处理（幂等性）
            Seat existingSeat = seatMapper.selectOne(
                    new LambdaQueryWrapper<Seat>()
                            .eq(Seat::getSessionId, message.getSessionId())
                            .eq(Seat::getTemplateSeatId, message.getSeatIds().get(0))
                            .eq(Seat::getStatus, "sold")
            );

            if (existingSeat != null && message.getOrderNo().equals(existingSeat.getOrderNo())) {
                log.info("座位已处理，跳过: orderNo={}, sessionId={}", message.getOrderNo(), message.getSessionId());
                return;
            }

            // 批量更新座位状态为已售出
            int updated = seatMapper.markSeatsSold(message.getSessionId(), message.getSeatIds(), message.getOrderNo());
            log.info("更新数据库座位状态为已售出: orderNo={}, sessionId={}, count={}",
                    message.getOrderNo(), message.getSessionId(), updated);

        } catch (Exception e) {
            log.error("处理订单创建消息异常: orderNo={}", message.getOrderNo(), e);
            throw e; // 抛出异常，触发重试
        }
    }
}

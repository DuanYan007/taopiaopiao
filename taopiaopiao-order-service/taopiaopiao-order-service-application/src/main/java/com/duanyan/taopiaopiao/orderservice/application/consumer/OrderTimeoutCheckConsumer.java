package com.duanyan.taopiaopiao.orderservice.application.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import com.duanyan.taopiaopiao.common.mq.message.OrderPaidMessage;
import com.duanyan.taopiaopiao.orderservice.application.client.PaymentClient;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentQueryResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentResult;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.application.producer.OrderCancelProducer;
import com.duanyan.taopiaopiao.orderservice.application.producer.OrderTransactionProducer;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_TIMEOUT_CHECK,
        consumerGroup = "order-service-timeout-check-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderTimeoutCheckConsumer implements RocketMQListener<OrderCancelMessage> {

    private final OrderMapper orderMapper;
    private final PaymentClient paymentClient;
    private final OrderCancelProducer orderCancelProducer;
    private final OrderTransactionProducer orderTransactionProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(OrderCancelMessage message) {
        String orderNo = message.getOrderNo();
        try {
            Order order = getOrder(orderNo);
            if (order == null) {
                log.warn("超时检查时订单不存在，跳过: orderNo={}", orderNo);
                return;
            }

            if (isTerminal(order)) {
                log.info("超时检查命中终态订单，跳过: orderNo={}, status={}", orderNo, order.getStatus());
                return;
            }

            PaymentResult<PaymentQueryResponse> result = paymentClient.queryPayment(orderNo);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("超时检查查询支付失败，稍后重试: orderNo={}", orderNo);
                throw new RuntimeException("查询支付状态失败");
            }

            PaymentQueryResponse payment = result.getData();
            if (payment.isSuccess()) {
                handlePaid(order, message);
                return;
            }

            handleTimeout(orderNo, message);
        } catch (Exception e) {
            log.error("处理超时检查消息异常: orderNo={}", orderNo, e);
            throw e;
        }
    }

    private void handlePaid(Order order, OrderCancelMessage message) {
        int updated = orderMapper.markPaidIfUnpaid(message.getOrderNo(), OrderStatus.PAID.getCode());
        if (updated == 1) {
            orderTransactionProducer.sendOrderPaidEvent(buildPaidMessage(order));
            log.info("超时检查命中已支付，已补发支付成功消息: orderNo={}", message.getOrderNo());
            return;
        }

        Order latest = getOrder(message.getOrderNo());
        if (latest != null && isTerminal(latest)) {
            log.info("超时检查支付兜底时状态已变化，跳过: orderNo={}, status={}", message.getOrderNo(), latest.getStatus());
            return;
        }

        throw new RuntimeException("更新订单为已支付失败");
    }

    private void handleTimeout(String orderNo, OrderCancelMessage message) {
        int updated = orderMapper.markTimeoutIfUnpaid(orderNo, OrderStatus.TIMEOUT.getCode());
        if (updated == 1) {
            orderCancelProducer.sendCancelMessage(message);
            log.info("超时检查确认未支付，已发送超时取消消息: orderNo={}", orderNo);
            return;
        }

        Order latest = getOrder(orderNo);
        if (latest != null && isTerminal(latest)) {
            log.info("超时检查取消兜底时状态已变化，跳过: orderNo={}, status={}", orderNo, latest.getStatus());
            return;
        }

        throw new RuntimeException("更新订单为超时失败");
    }

    private OrderPaidMessage buildPaidMessage(Order order) {
        List<String> seatIds = StringUtils.isBlank(order.getSeatIds())
                ? List.of()
                : List.of(order.getSeatIds().split(","));
        return OrderPaidMessage.builder()
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .lockId(order.getLockId())
                .sessionId(order.getSessionId())
                .eventId(order.getEventId())
                .seatIds(seatIds)
                .seatCount(order.getSeatCount())
                .unitPrice(order.getUnitPrice())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .expireTime(order.getExpireTime())
                .build();
    }

    private Order getOrder(String orderNo) {
        return orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)
        );
    }

    private boolean isTerminal(Order order) {
        return OrderStatus.PAID.getCode().equals(order.getStatus())
                || OrderStatus.CANCELLED.getCode().equals(order.getStatus())
                || OrderStatus.TIMEOUT.getCode().equals(order.getStatus())
                || OrderStatus.REFUNDED.getCode().equals(order.getStatus());
    }
}

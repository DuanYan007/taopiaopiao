package com.duanyan.taopiaopiao.orderservice.application.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import com.duanyan.taopiaopiao.common.mq.message.OrderCreatedMessage;
import com.duanyan.taopiaopiao.orderservice.application.client.PaymentClient;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentQueryResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentResult;
import com.duanyan.taopiaopiao.orderservice.application.producer.OrderCancelProducer;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单事务消息监听器
 * <p>
 * 处理 ORDER_CREATED 事务消息
 * 阶段1：executeLocalTransaction - 执行本地事务（创建订单、发送延迟消息）
 * 阶段2：checkLocalTransaction - 回查本地事务状态
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQTransactionListener(rocketMQTemplateBeanName = "rocketMQTemplate")
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    private final OrderMapper orderMapper;
    private final OrderCancelProducer orderCancelProducer;
    private final PaymentClient paymentClient;

    /**
     * 阶段1：执行本地事务
     * <p>
     * 发送半消息成功后，RocketMQ 回调此方法执行真正的本地事务
     *
     * @param msg 消息
     * @param arg 参数（OrderCreatedMessage）
     * @return 事务状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        // 解析订单号
        String orderNo = (String) msg.getHeaders().get("RocketMQMessageKeys");
        log.info("执行本地事务: orderNo={}", orderNo);

        try {
            // 从 arg 中获取创建订单的参数
            if (!(arg instanceof OrderCreatedMessage)) {
                log.error("参数类型错误: {}", arg.getClass());
                return RocketMQLocalTransactionState.ROLLBACK;
            }

            OrderCreatedMessage message = (OrderCreatedMessage) arg;

            // 创建订单
            Order order = Order.builder()
                    .orderNo(orderNo)
                    .userId(message.getUserId())
                    .sessionId(message.getSessionId())
                    .eventId(message.getEventId())
                    .seatIds(String.join(",", message.getSeatIds()))
                    .seatCount(message.getSeatCount())
                    .unitPrice(message.getUnitPrice())
                    .totalAmount(message.getTotalAmount())
                    .status(OrderStatus.UNPAID.getCode())
                    .createdAt(LocalDateTime.now())
                    .expireTime(message.getExpireTime())
                    .build();

            orderMapper.insert(order);

            log.info("本地事务成功，订单已创建: orderNo={}, userId={}, amount={}",
                    orderNo, message.getUserId(), message.getTotalAmount());

            // 订单创建成功后，发送延迟消息（超时取消）
            OrderCancelMessage cancelMessage = OrderCancelMessage.builder()
                    .orderNo(orderNo)
                    .userId(message.getUserId())
                    .sessionId(message.getSessionId())
                    .seatIds(message.getSeatIds())
                    .reason("TIMEOUT")
                    .build();
            orderCancelProducer.sendDelayCancelMessage(cancelMessage, 9); // 延迟等级9 = 5分钟

            log.info("延迟取消消息已发送: orderNo={}", orderNo);

            // 返回 UNKNOWN，等待支付完成后通过回查确认
            return RocketMQLocalTransactionState.UNKNOWN;

        } catch (Exception e) {
            log.error("执行本地事务异常: orderNo={}", orderNo, e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 阶段2：回查本地事务
     * <p>
     * 当事务状态为 UNKNOWN 时，RocketMQ 会调用此方法回查
     * 这里查询支付系统来决定是否提交消息
     *
     * @param msg 消息
     * @return 事务状态
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // 从消息体获取订单号（消费者也是从消息体获取）
        Object payload = msg.getPayload();
        String orderNo = null;

        if (payload instanceof OrderCreatedMessage) {
            orderNo = ((OrderCreatedMessage) payload).getOrderNo();
        } else {
            // 兼容：从 header 获取
            orderNo = (String) msg.getHeaders().get("RocketMQMessageKeys");
        }

        log.info("回查本地事务: orderNo={}", orderNo);

        try {
            // 查询支付系统获取真实支付状态
            PaymentResult<PaymentQueryResponse> result = paymentClient.queryPayment(orderNo);

            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("查询支付系统失败，稍后重试: orderNo={}", orderNo);
                return RocketMQLocalTransactionState.UNKNOWN;
            }

            PaymentQueryResponse payment = result.getData();

            // 根据支付状态决定事务消息的提交/回滚
            if (payment.isSuccess()) {
                log.info("订单已支付，提交事务消息: orderNo={}", orderNo);
                return RocketMQLocalTransactionState.COMMIT;
            } else if (payment.isNotFound()) {
                log.warn("支付记录不存在，回滚事务消息: orderNo={}", orderNo);
                return RocketMQLocalTransactionState.ROLLBACK;
            } else if (payment.isPending()) {
                log.info("订单仍待支付，稍后重试: orderNo={}", orderNo);
                return RocketMQLocalTransactionState.UNKNOWN;
            } else {
                log.warn("支付失败或已取消，回滚事务消息: orderNo={}, status={}", orderNo, payment.getStatus());
                return RocketMQLocalTransactionState.ROLLBACK;
            }

        } catch (Exception e) {
            log.error("回查本地事务异常: orderNo={}", orderNo, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}

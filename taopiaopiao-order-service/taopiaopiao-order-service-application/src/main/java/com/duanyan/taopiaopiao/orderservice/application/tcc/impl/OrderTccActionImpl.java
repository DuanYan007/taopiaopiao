package com.duanyan.taopiaopiao.orderservice.application.tcc.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderPrepareMapper;
import com.duanyan.taopiaopiao.orderservice.application.support.TestFailPointSupport;
import com.duanyan.taopiaopiao.orderservice.application.tcc.OrderTccAction;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.orderservice.domain.entity.OrderPrepare;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderPrepareStatus;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderStatus;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTccActionImpl implements OrderTccAction {

    private static final String TIMEOUT_CHECK_DESTINATION = MqTopic.ORDER_TOPIC + ":" + MqTopic.TAG_TIMEOUT_CHECK;

    private final OrderPrepareMapper orderPrepareMapper;
    private final OrderMapper orderMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final TestFailPointSupport testFailPointSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryPrepareOrder(BusinessActionContext actionContext,
                                   String orderNo,
                                   Long userId,
                                   Long sessionId,
                                   Long eventId,
                                   List<String> seatIds,
                                   Integer seatCount,
                                   java.math.BigDecimal unitPrice,
                                   java.math.BigDecimal totalAmount,
                                   LocalDateTime expireTime) {
        BusinessActionContextUtil.addContext(buildActionContext(
                orderNo, userId, sessionId, eventId, seatIds, seatCount, unitPrice, totalAmount, expireTime));
        OrderPrepare existing = orderPrepareMapper.selectOne(
                new LambdaQueryWrapper<OrderPrepare>()
                        .eq(OrderPrepare::getOrderNo, orderNo)
        );
        if (existing != null) {
            if (OrderPrepareStatus.CANCELED.getCode().equals(existing.getStatus())) {
                log.warn("订单 TCC Try 命中空回滚标记: xid={}, orderNo={}", RootContext.getXID(), orderNo);
                return false;
            }
            return true;
        }

        OrderPrepare prepare = OrderPrepare.builder()
                .orderNo(orderNo)
                .xid(RootContext.getXID())
                .userId(userId)
                .sessionId(sessionId)
                .eventId(eventId)
                .seatIds(seatIds)
                .seatCount(seatCount)
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .expireTime(expireTime)
                .status(OrderPrepareStatus.PREPARED.getCode())
                .build();
        orderPrepareMapper.insert(prepare);
        testFailPointSupport.failOnce("ORDER_TRY_AFTER_PREPARE_INSERT");
        log.info("订单 TCC Try 成功: xid={}, orderNo={}", RootContext.getXID(), orderNo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmPrepareOrder(BusinessActionContext actionContext) {
        String orderNo = getStringValue(actionContext, "orderNo");
        if (orderNo == null) {
            log.warn("订单 TCC Confirm 缺少 orderNo，按空确认跳过: xid={}, actionContext={}",
                    actionContext == null ? null : actionContext.getXid(),
                    actionContext == null ? null : actionContext.getActionContext());
            return true;
        }
        OrderPrepare prepare = getPrepare(orderNo);
        if (prepare == null) {
            return true;
        }
        if (OrderPrepareStatus.CONFIRMED.getCode().equals(prepare.getStatus())) {
            return true;
        }
        if (OrderPrepareStatus.CANCELED.getCode().equals(prepare.getStatus())) {
            return false;
        }

        Order existingOrder = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)
        );
        if (existingOrder == null) {
            Order order = Order.builder()
                    .orderNo(prepare.getOrderNo())
                    .userId(prepare.getUserId())
                    .sessionId(prepare.getSessionId())
                    .eventId(prepare.getEventId())
                    .seatIds(prepare.getSeatIds())
                    .seatCount(prepare.getSeatCount())
                    .unitPrice(prepare.getUnitPrice())
                    .totalAmount(prepare.getTotalAmount())
                    .status(OrderStatus.UNPAID.getCode())
                    .expireTime(prepare.getExpireTime())
                    .build();
            orderMapper.insert(order);
            testFailPointSupport.failOnce("ORDER_CONFIRM_AFTER_ORDER_INSERT");
        }

        testFailPointSupport.failOnce("ORDER_CONFIRM_BEFORE_PREPARE_UPDATE");
        prepare.setStatus(OrderPrepareStatus.CONFIRMED.getCode());
        orderPrepareMapper.updateById(prepare);
        testFailPointSupport.failOnce("ORDER_CONFIRM_BEFORE_TIMEOUT_MESSAGE");
        sendTimeoutCheckMessage(prepare);
        log.info("订单 TCC Confirm 成功: orderNo={}", orderNo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelPrepareOrder(BusinessActionContext actionContext) {
        String orderNo = getStringValue(actionContext, "orderNo");
        if (orderNo == null) {
            log.warn("订单 TCC Cancel 缺少 orderNo，按空回滚跳过: xid={}, actionContext={}",
                    actionContext == null ? null : actionContext.getXid(),
                    actionContext == null ? null : actionContext.getActionContext());
            return true;
        }
        OrderPrepare prepare = getPrepare(orderNo);
        if (prepare == null) {
            insertCancelMarker(orderNo, actionContext.getXid());
            log.info("订单 TCC Cancel 空回滚成功: orderNo={}", orderNo);
            return true;
        }
        if (OrderPrepareStatus.CANCELED.getCode().equals(prepare.getStatus())) {
            return true;
        }
        if (OrderPrepareStatus.CONFIRMED.getCode().equals(prepare.getStatus())) {
            return false;
        }
        prepare.setStatus(OrderPrepareStatus.CANCELED.getCode());
        orderPrepareMapper.updateById(prepare);
        testFailPointSupport.failOnce("ORDER_CANCEL_AFTER_PREPARE_UPDATE");
        log.info("订单 TCC Cancel 成功: orderNo={}", orderNo);
        return true;
    }

    private void sendTimeoutCheckMessage(OrderPrepare prepare) {
        long deliverTimeMillis = resolveDeliverTimeMillis(prepare.getExpireTime());
        Message<OrderCancelMessage> message = MessageBuilder.withPayload(OrderCancelMessage.builder()
                        .orderNo(prepare.getOrderNo())
                        .userId(prepare.getUserId())
                        .sessionId(prepare.getSessionId())
                        .seatIds(prepare.getSeatIds())
                        .reason("TIMEOUT")
                        .build())
                .setHeader("KEYS", prepare.getOrderNo())
                .build();

        SendResult sendResult = rocketMQTemplate.syncSendDeliverTimeMills(
                TIMEOUT_CHECK_DESTINATION,
                message,
                deliverTimeMillis
        );
        log.info("订单 TCC Confirm 发送超时检查消息成功: orderNo={}, msgId={}",
                prepare.getOrderNo(), sendResult.getMsgId());
    }

    private long resolveDeliverTimeMillis(LocalDateTime expireTime) {
        if (expireTime == null) {
            throw new IllegalArgumentException("expireTime 不能为空");
        }
        long targetMillis = expireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return Math.max(targetMillis, System.currentTimeMillis() + 1000L);
    }

    private OrderPrepare getPrepare(String orderNo) {
        return orderPrepareMapper.selectOne(
                new LambdaQueryWrapper<OrderPrepare>()
                        .eq(OrderPrepare::getOrderNo, orderNo)
        );
    }

    private String getStringValue(BusinessActionContext actionContext, String key) {
        if (actionContext == null) {
            return null;
        }
        Object value = actionContext.getActionContext(key);
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> buildActionContext(String orderNo,
                                                   Long userId,
                                                   Long sessionId,
                                                   Long eventId,
                                                   List<String> seatIds,
                                                   Integer seatCount,
                                                   java.math.BigDecimal unitPrice,
                                                   java.math.BigDecimal totalAmount,
                                                   LocalDateTime expireTime) {
        Map<String, Object> actionContext = new LinkedHashMap<>();
        actionContext.put("orderNo", orderNo);
        actionContext.put("userId", userId);
        actionContext.put("sessionId", sessionId);
        actionContext.put("eventId", eventId);
        actionContext.put("seatIds", seatIds);
        actionContext.put("seatCount", seatCount);
        actionContext.put("unitPrice", unitPrice);
        actionContext.put("totalAmount", totalAmount);
        actionContext.put("expireTime", expireTime);
        return actionContext;
    }

    private void insertCancelMarker(String orderNo, String xid) {
        try {
            orderPrepareMapper.insert(OrderPrepare.builder()
                    .orderNo(orderNo)
                    .xid(xid)
                    .status(OrderPrepareStatus.CANCELED.getCode())
                    .build());
            testFailPointSupport.failOnce("ORDER_CANCEL_AFTER_EMPTY_ROLLBACK_INSERT");
        } catch (DuplicateKeyException ignore) {
            log.info("订单 TCC Cancel 标记已存在: orderNo={}", orderNo);
        }
    }
}

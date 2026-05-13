package com.duanyan.taopiaopiao.orderservice.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duanyan.taopiaopiao.common.dto.CancelOrderRequest;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.api.dto.*;
import com.duanyan.taopiaopiao.orderservice.application.client.EventClient;
import com.duanyan.taopiaopiao.orderservice.application.client.PaymentClient;
import com.duanyan.taopiaopiao.orderservice.application.client.SeckillInternalClient;
import com.duanyan.taopiaopiao.orderservice.application.client.SeatTemplateClient;
import com.duanyan.taopiaopiao.orderservice.application.client.SessionClient;
import com.duanyan.taopiaopiao.orderservice.application.client.VenueClient;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.EventResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentCreateRequest;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentCreateResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentQueryResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentResult;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.SeatTemplateResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.SessionResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.VenueResponse;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.application.service.OrderService;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final long POLL_MS_STOP = 0L;
    private static final long POLL_MS_READY = 0L;
    private static final long POLL_MS_UNPAID_NOT_READY = 1500L;
    private static final long POLL_MS_SLOW_PATH = 2500L;

    private final OrderMapper orderMapper;
    private final SessionClient sessionClient;
    private final EventClient eventClient;
    private final VenueClient venueClient;
    private final SeatTemplateClient seatTemplateClient;
    private final PaymentClient paymentClient;
    private final SeckillInternalClient seckillInternalClient;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public OrderPageResponse getOrderPage(Long userId, OrderPageRequest request) {
        // 构建查询条件
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt);

        if (request.getStatus() != null) {
            queryWrapper.eq(Order::getStatus, request.getStatus());
        }

        // 分页查询
        Page<Order> page = new Page<>(request.getPage(), request.getPageSize());
        Page<Order> orderPage = orderMapper.selectPage(page, queryWrapper);

        // 转换为响应DTO
        List<OrderPageResponse.OrderListItem> listItems = orderPage.getRecords().stream()
                .map(this::buildOrderListItem)
                .collect(Collectors.toList());

        return OrderPageResponse.builder()
                .list(listItems)
                .total(orderPage.getTotal())
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .build();
    }

    @Override
    public OrderResponse getOrderByNo(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)
                        .eq(Order::getUserId, userId)
        );

        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }

        OrderResponse response = buildOrderResponse(order);
        enrichPaymentInfo(response, order);
        return response;
    }

    @Override
    @Transactional
    public Boolean cancelOrder(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)
                        .eq(Order::getUserId, userId)
        );

        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        if (!OrderStatus.UNPAID.getCode().equals(order.getStatus())) {
            throw new RuntimeException("只能取消待支付订单");
        }

        List<String> seatIds = order.getSeatIds() == null ? List.of() : order.getSeatIds();

        int updated = orderMapper.markCancelledIfUnpaid(orderNo, OrderStatus.CANCELLED.getCode());
        if (updated != 1) {
            Order latest = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getOrderNo, orderNo)
            );
            if (latest != null && OrderStatus.CANCELLED.getCode().equals(latest.getStatus())) {
                log.info("订单已由其他链路更新为取消状态，跳过重复取消: orderNo={}", orderNo);
                return true;
            }
            if (latest != null) {
                throw new RuntimeException("订单状态已变化，无法取消: " + latest.getStatus());
            }
            throw new RuntimeException("取消订单时更新状态失败");
        }

        Result<Boolean> cancelResult = seckillInternalClient.cancelOrder(CancelOrderRequest.builder()
                .orderNo(orderNo)
                .userId(userId)
                .sessionId(order.getSessionId())
                .seatIds(seatIds)
                .reason("USER")
                .build());
        if (cancelResult == null || !cancelResult.isSuccess() || !Boolean.TRUE.equals(cancelResult.getData())) {
            throw new RuntimeException("取消订单后释放座位失败");
        }

        log.info("订单取消成功: orderNo={}, userId={}", orderNo, userId);
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteOrder(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)
                        .eq(Order::getUserId, userId)
        );

        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        // 只能删除已取消或已退款的订单
        if (!OrderStatus.CANCELLED.getCode().equals(order.getStatus())
                && !OrderStatus.REFUNDED.getCode().equals(order.getStatus())) {
            throw new RuntimeException("只能删除已取消或已退款的订单");
        }

        orderMapper.deleteById(order.getId());

        log.info("订单删除成功: orderNo={}, userId={}", orderNo, userId);
        return true;
    }

    private OrderResponse buildOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        BeanUtils.copyProperties(order, response);

        // 设置状态描述
        OrderStatus status = OrderStatus.fromCode(order.getStatus());
        if (status != null) {
            response.setStatusDesc(status.getDesc());
        }

        response.setNextPollMs(resolveNextPollMs(response));

        return response;
    }

    private void enrichPaymentInfo(OrderResponse response, Order order) {
        if (order == null || response == null) {
            return;
        }

        if (!OrderStatus.UNPAID.getCode().equals(order.getStatus())) {
            response.setPaymentStatus(OrderStatus.PAID.getCode().equals(order.getStatus()) ? "SUCCESS" : "NOT_AVAILABLE");
            response.setNextPollMs(resolveNextPollMs(response));
            return;
        }

        try {
            PaymentResult<PaymentQueryResponse> queryResult = paymentClient.queryPayment(order.getOrderNo());
            if (queryResult == null || !queryResult.isSuccess() || queryResult.getData() == null) {
                response.setPaymentStatus("NOT_READY");
                response.setNextPollMs(resolveNextPollMs(response));
                return;
            }

            PaymentQueryResponse payment = queryResult.getData();
            if (payment.isSuccess()) {
                response.setPaymentStatus("SUCCESS");
                response.setNextPollMs(resolveNextPollMs(response));
                return;
            }

            if (payment.isPending()) {
                response.setPaymentStatus("READY");
                response.setPayUrl(buildPayUrl(order.getOrderNo()));
                response.setNextPollMs(resolveNextPollMs(response));
                return;
            }

            if (payment.isNotFound()) {
                PaymentResult<PaymentCreateResponse> createResult = paymentClient.createPayment(buildPaymentCreateRequest(order));
                if (createResult != null && createResult.isSuccess() && createResult.getData() != null) {
                    response.setPaymentStatus("READY");
                    response.setPayUrl(createResult.getData().getPayUrl());
                    response.setNextPollMs(resolveNextPollMs(response));
                    return;
                }
            }

            response.setPaymentStatus("NOT_READY");
            response.setNextPollMs(resolveNextPollMs(response));
        } catch (Exception e) {
            log.warn("补齐支付信息失败: orderNo={}", order.getOrderNo(), e);
            response.setPaymentStatus("NOT_READY");
            response.setNextPollMs(resolveNextPollMs(response));
        }
    }

    private PaymentCreateRequest buildPaymentCreateRequest(Order order) {
        return PaymentCreateRequest.builder()
                .orderNo(order.getOrderNo())
                .amount(order.getTotalAmount())
                .payMethod("MOCK")
                .body("演唱会门票")
                .build();
    }

    private String buildPayUrl(String orderNo) {
        return "/payment/simulate/success?orderNo=" + orderNo;
    }

    private long resolveNextPollMs(OrderResponse response) {
        if (response == null) {
            return POLL_MS_UNPAID_NOT_READY;
        }
        return resolveNextPollMs(response.getStatus(), response.getPaymentStatus(), response.getExpireTime());
    }

    private long resolveNextPollMs(Integer status, String paymentStatus, LocalDateTime expireTime) {
        if (status != null) {
            if (OrderStatus.PAID.getCode().equals(status)
                    || OrderStatus.CANCELLED.getCode().equals(status)
                    || OrderStatus.TIMEOUT.getCode().equals(status)
                    || OrderStatus.REFUNDED.getCode().equals(status)) {
                return POLL_MS_STOP;
            }
        }

        if ("SUCCESS".equals(paymentStatus) || "READY".equals(paymentStatus) || "NOT_AVAILABLE".equals(paymentStatus)) {
            return "NOT_READY".equals(paymentStatus) ? POLL_MS_UNPAID_NOT_READY : POLL_MS_READY;
        }

        if (expireTime != null) {
            long secondsLeft = Duration.between(LocalDateTime.now(), expireTime).getSeconds();
            if (secondsLeft <= 0) {
                return POLL_MS_STOP;
            }
            if (secondsLeft <= 15) {
                return POLL_MS_SLOW_PATH;
            }
            if (secondsLeft <= 60) {
                return POLL_MS_UNPAID_NOT_READY;
            }
        }

        if (status != null && OrderStatus.UNPAID.getCode().equals(status)) {
            return POLL_MS_UNPAID_NOT_READY;
        }

        return POLL_MS_UNPAID_NOT_READY;
    }

    private OrderPageResponse.OrderListItem buildOrderListItem(Order order) {
        OrderPageResponse.OrderListItem item = OrderPageResponse.OrderListItem.builder()
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .eventId(order.getEventId())
                .seatCount(order.getSeatCount())
                .unitPrice(order.getUnitPrice())
                .totalAmount(order.getTotalAmount())
                .build();

        // 设置状态描述
        OrderStatus orderStatus = OrderStatus.fromCode(order.getStatus());
        if (orderStatus != null) {
            item.setStatusDesc(orderStatus.getDesc());
        }

        // 格式化时间
        if (order.getCreatedAt() != null) {
            item.setCreatedAt(order.getCreatedAt().format(DATE_TIME_FORMATTER));
        }
        if (order.getPayTime() != null) {
            item.setPayTime(order.getPayTime().format(DATE_TIME_FORMATTER));
        }
        if (order.getCancelTime() != null) {
            item.setCancelTime(order.getCancelTime().format(DATE_TIME_FORMATTER));
        }
        if (order.getRefundTime() != null) {
            item.setRefundTime(order.getRefundTime().format(DATE_TIME_FORMATTER));
        }

        // 获取场次信息
        Result<SessionResponse> sessionResult = sessionClient.getSessionById(order.getSessionId());
        if (sessionResult != null && sessionResult.getData() != null) {
            SessionResponse session = sessionResult.getData();
            if (session.getStartTime() != null) {
                item.setStartTime(session.getStartTime().format(DATE_TIME_FORMATTER));
            }
        }

        // 获取演出信息
        Result<EventResponse> eventResult = eventClient.getEventById(order.getEventId());
        if (eventResult != null && eventResult.getData() != null) {
            EventResponse event = eventResult.getData();
            item.setEventName(event.getName());
            item.setEventCover(event.getCoverImage());
        }

        // 获取场馆信息（通过场次 -> 座位模板 -> 场馆）
        Result<SessionResponse> sessionForVenue = sessionClient.getSessionById(order.getSessionId());
        if (sessionForVenue != null && sessionForVenue.getData() != null) {
            SessionResponse session = sessionForVenue.getData();
            if (session.getSeatTemplateId() != null) {
                Result<SeatTemplateResponse> templateResult = seatTemplateClient.getTemplateById(session.getSeatTemplateId());
                if (templateResult != null && templateResult.getData() != null) {
                    SeatTemplateResponse template = templateResult.getData();
                    if (template.getVenueId() != null) {
                        Result<VenueResponse> venueResult = venueClient.getVenueById(template.getVenueId());
                        if (venueResult != null && venueResult.getData() != null) {
                            item.setVenueName(venueResult.getData().getName());
                        }
                    }
                }
            }
        }

        // 构建座位信息（简化版，仅显示座位数量）
        item.setSeatInfo(order.getSeatCount() + "张座位");

        // 座位详情（暂时为空，需要从订单扩展表或Redis获取详细信息）
        item.setSeatDetails(new ArrayList<>());

        return item;
    }
}

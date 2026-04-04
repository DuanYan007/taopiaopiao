package com.duanyan.taopiaopiao.orderservice.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.api.dto.*;
import com.duanyan.taopiaopiao.orderservice.application.client.EventClient;
import com.duanyan.taopiaopiao.orderservice.application.client.SeatTemplateClient;
import com.duanyan.taopiaopiao.orderservice.application.client.SessionClient;
import com.duanyan.taopiaopiao.orderservice.application.client.VenueClient;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.EventResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.MarkSeatsSoldRequest;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.SeatTemplateResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.SessionResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.VenueResponse;
import com.duanyan.taopiaopiao.orderservice.application.config.OrderIdGenerator;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.application.producer.OrderCancelProducer;
import com.duanyan.taopiaopiao.orderservice.application.producer.OrderTransactionProducer;
import com.duanyan.taopiaopiao.orderservice.application.service.OrderService;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private final OrderMapper orderMapper;
    private final SessionClient sessionClient;
    private final EventClient eventClient;
    private final VenueClient venueClient;
    private final SeatTemplateClient seatTemplateClient;
    private final OrderIdGenerator orderIdGenerator;
    private final OrderCancelProducer orderCancelProducer;
    private final OrderTransactionProducer orderTransactionProducer;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public OrderResponse createPendingOrder(CreatePendingOrderRequest request) {
        // 使用雪花算法生成订单号
        String orderNo = String.valueOf(orderIdGenerator.nextId());

        log.info("开始创建待支付订单: orderNo={}, userId={}, sessionId={}",
                orderNo, request.getUserId(), request.getSessionId());

        // 发送事务消息（半消息）
        // executeLocalTransaction 会被回调，在那里执行真正的本地事务（创建订单、发送延迟消息）
        boolean sent = orderTransactionProducer.sendOrderPaidMessage(orderNo, request);

        if (!sent) {
            log.error("发送事务消息失败: orderNo={}", orderNo);
            throw new RuntimeException("创建订单失败，请重试");
        }

        log.info("事务消息发送成功: orderNo={}", orderNo);

        // 返回订单信息（此时订单可能还未创建，但订单号已生成）
        return OrderResponse.builder()
                .orderNo(orderNo)
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .eventId(request.getEventId())
                .seatIds(request.getSeatIds())
                .seatCount(request.getSeatCount())
                .unitPrice(request.getUnitPrice())
                .totalAmount(request.getTotalAmount())
                .status(OrderStatus.UNPAID.getCode())
                .statusDesc(OrderStatus.UNPAID.getDesc())
                .build();
    }

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
            return null;
        }

        return buildOrderResponse(order);
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

        List<String> seatIds = List.of(order.getSeatIds().split(","));

        // 1. 先更新订单状态，确保本地状态先进入取消态
        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setCancelTime(LocalDateTime.now());
        order.setUpdatedAt(null); // 让 MyBatis-Plus 自动填充
        orderMapper.updateById(order);

        // 2. 再发送取消消息（异步释放座位）
        OrderCancelMessage cancelMessage = OrderCancelMessage.builder()
                .orderNo(orderNo)
                .userId(userId)
                .sessionId(order.getSessionId())
                .seatIds(seatIds)
                .reason("USER")  // 用户主动取消
                .build();
        orderCancelProducer.sendCancelMessage(cancelMessage);

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

        // 解析座位ID列表
        if (order.getSeatIds() != null) {
            response.setSeatIds(List.of(order.getSeatIds().split(",")));
        }

        return response;
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

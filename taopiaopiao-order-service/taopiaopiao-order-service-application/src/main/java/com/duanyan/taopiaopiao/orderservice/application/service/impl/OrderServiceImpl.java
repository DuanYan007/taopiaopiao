package com.duanyan.taopiaopiao.orderservice.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duanyan.taopiaopiao.common.mq.message.OrderCancelMessage;
import com.duanyan.taopiaopiao.common.mq.message.PaymentSuccessMessage;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.api.dto.*;
import com.duanyan.taopiaopiao.orderservice.application.client.EventClient;
import com.duanyan.taopiaopiao.orderservice.application.client.SeatTemplateClient;
import com.duanyan.taopiaopiao.orderservice.application.client.SeckillInternalClient;
import com.duanyan.taopiaopiao.orderservice.application.client.SessionClient;
import com.duanyan.taopiaopiao.orderservice.application.client.VenueClient;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.EventDTO;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.MarkSeatsSoldRequest;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.SeatTemplateDTO;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.SessionDTO;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.VenueDTO;
import com.duanyan.taopiaopiao.orderservice.application.config.OrderIdGenerator;
import com.duanyan.taopiaopiao.orderservice.application.controller.dto.CreatePendingOrderRequest;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.application.producer.OrderCancelProducer;
import com.duanyan.taopiaopiao.orderservice.application.producer.PaymentSuccessProducer;
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
    private final SeckillInternalClient seckillInternalClient;
    private final SessionClient sessionClient;
    private final EventClient eventClient;
    private final VenueClient venueClient;
    private final SeatTemplateClient seatTemplateClient;
    private final OrderIdGenerator orderIdGenerator;
    private final PaymentSuccessProducer paymentSuccessProducer;
    private final OrderCancelProducer orderCancelProducer;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    @Override
    @Transactional
    public OrderResponse createPendingOrder(CreatePendingOrderRequest request) {
        // 使用雪花算法生成订单号
        String orderNo = String.valueOf(orderIdGenerator.nextId());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusMinutes(5); // 15分钟过期

        // 获取场次信息确定eventId
        Long eventId = request.getEventId();
        // 创建待支付订单
        Order order = Order.builder()
                .orderNo(orderNo)
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .eventId(eventId)
                .seatIds(String.join(",", request.getSeatIds()))
                .seatCount(request.getSeatCount())
                .unitPrice(request.getUnitPrice())
                .totalAmount(request.getTotalAmount())
                .status(OrderStatus.UNPAID.getCode())
                .expireTime(expireTime)
                .build();

        orderMapper.insert(order);

        // 发送 15 分钟延时消息（超时取消）
        OrderCancelMessage cancelMessage = OrderCancelMessage.builder()
                .orderNo(orderNo)
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .seatIds(request.getSeatIds())
                .reason("TIMEOUT")
                .build();
        orderCancelProducer.sendDelayCancelMessage(cancelMessage, 1);

        log.info("创建待支付订单成功: orderNo={}, userId={}, amount={}, seats={}",
                orderNo, request.getUserId(), request.getTotalAmount(), request.getSeatIds());

        return buildOrderResponse(order);
    }

    /**
     * 支付已有订单
     */
    @Override
    @Transactional
    public OrderResponse pay(Long userId, CreateOrderRequest request) {
        if (request.getOrderNo() == null || request.getOrderNo().isBlank()) {
            throw new RuntimeException("订单号不能为空");
        }
        // 查询订单
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, request.getOrderNo())
                        .eq(Order::getUserId, userId)
        );

        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        if (!OrderStatus.UNPAID.getCode().equals(order.getStatus())) {
            throw new RuntimeException("订单状态异常，无法支付");
        }

        // 检查是否过期
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("订单已过期");
        }

        List<String> seatIds = List.of(order.getSeatIds().split(","));

        // 1. Redis 确认购买（状态 1 → 2）
        // 2. 更新 seat_locks 状态为 2（已支付）
        Result<Integer> markResult = seckillInternalClient.markSeatLocksPaid(
                order.getOrderNo(), order.getSessionId(), userId, seatIds);
        if (markResult == null || !markResult.isSuccess()) {
            throw new RuntimeException("确认购买失败");
        }

        // 3. 更新订单状态
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.PAID.getCode());
        order.setPayTime(now);
        order.setUpdatedAt(null); // 让 MyBatis-Plus 自动填充
        orderMapper.updateById(order);

        // 4. 发送 RocketMQ 消息异步更新 seats 表
        // 注意：本地事务（订单状态更新、Redis 更新）已在上面完成
        // 此处发送消息通知 SessionService 更新 seats 表，消息发送失败不影响支付结果
        PaymentSuccessMessage message = PaymentSuccessMessage.builder()
                .orderNo(order.getOrderNo())
                .userId(userId)
                .sessionId(order.getSessionId())
                .seatIds(seatIds)
                .amount(order.getTotalAmount())
                .payTime(now)
                .build();
        paymentSuccessProducer.sendPaymentSuccessMessage(message);

        log.info("支付成功: orderNo={}, userId={}, amount={}", order.getOrderNo(), userId, order.getTotalAmount());

        return buildOrderResponse(order);
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

        // 1. 发送取消消息（异步释放座位）
        OrderCancelMessage cancelMessage = OrderCancelMessage.builder()
                .orderNo(orderNo)
                .userId(userId)
                .sessionId(order.getSessionId())
                .seatIds(seatIds)
                .reason("USER")  // 用户主动取消
                .build();
        orderCancelProducer.sendCancelMessage(cancelMessage);

        // 2. 更新订单状态
        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setCancelTime(LocalDateTime.now());
        order.setUpdatedAt(null); // 让 MyBatis-Plus 自动填充
        orderMapper.updateById(order);

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

    // 该方法已废弃，改用 RocketMQ 延时消息处理超时取消
    // @Override
    // @Transactional
    // public void cancelTimeoutOrders() { ... }

      // 之前使用的订单号生成算法
//    private String generateOrderNo() {
//        return "ORD" + System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
//    }

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
        Result<SessionDTO> sessionResult = sessionClient.getSessionById(order.getSessionId());
        if (sessionResult != null && sessionResult.getData() != null) {
            SessionDTO session = sessionResult.getData();
            if (session.getStartTime() != null) {
                item.setStartTime(session.getStartTime().format(DATE_TIME_FORMATTER));
            }
        }

        // 获取演出信息
        Result<EventDTO> eventResult = eventClient.getEventById(order.getEventId());
        if (eventResult != null && eventResult.getData() != null) {
            EventDTO event = eventResult.getData();
            item.setEventName(event.getName());
            item.setEventCover(event.getCoverImage());
        }

        // 获取场馆信息（通过场次 -> 座位模板 -> 场馆）
        Result<SessionDTO> sessionForVenue = sessionClient.getSessionById(order.getSessionId());
        if (sessionForVenue != null && sessionForVenue.getData() != null) {
            SessionDTO session = sessionForVenue.getData();
            if (session.getSeatTemplateId() != null) {
                Result<SeatTemplateDTO> templateResult = seatTemplateClient.getTemplateById(session.getSeatTemplateId());
                if (templateResult != null && templateResult.getData() != null) {
                    SeatTemplateDTO template = templateResult.getData();
                    if (template.getVenueId() != null) {
                        Result<VenueDTO> venueResult = venueClient.getVenueById(template.getVenueId());
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

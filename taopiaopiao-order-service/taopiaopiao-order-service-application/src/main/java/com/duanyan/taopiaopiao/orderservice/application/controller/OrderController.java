package com.duanyan.taopiaopiao.orderservice.application.controller;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.api.dto.CreateOrderRequest;
import com.duanyan.taopiaopiao.orderservice.api.dto.OrderPageRequest;
import com.duanyan.taopiaopiao.orderservice.api.dto.OrderPageResponse;
import com.duanyan.taopiaopiao.orderservice.api.dto.OrderResponse;
import com.duanyan.taopiaopiao.orderservice.application.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器
 */
@Slf4j
@Tag(name = "订单管理", description = "订单相关接口")
@RestController
@RequestMapping("/client/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "订单列表（分页）")
    public Result<OrderPageResponse> getOrderPage(@RequestHeader("X-User-Id") Long userId,
                                                    OrderPageRequest request) {
        OrderPageResponse response = orderService.getOrderPage(userId, request);
        return Result.success(response);
    }

    @PostMapping
    @Operation(summary = "支付订单")
    public Result<OrderResponse> createOrder(@RequestHeader("X-User-Id") Long userId,
                                              @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.pay(userId, request);
        return Result.success(response);
    }

    @GetMapping("/{orderNo}")
    @Operation(summary = "查询订单")
    public Result<OrderResponse> getOrderByNo(@RequestHeader("X-User-Id") Long userId,
                                               @PathVariable String orderNo) {
        OrderResponse response = orderService.getOrderByNo(userId, orderNo);
        return Result.success(response);
    }

    @PostMapping("/{orderNo}/cancel")
    @Operation(summary = "取消订单")
    public Result<Boolean> cancelOrder(@RequestHeader("X-User-Id") Long userId,
                                        @PathVariable String orderNo) {
        return Result.success(orderService.cancelOrder(userId, orderNo));
    }

    @PostMapping("/{orderNo}/delete")
    @Operation(summary = "删除订单")
    public Result<Boolean> deleteOrder(@RequestHeader("X-User-Id") Long userId,
                                        @PathVariable String orderNo) {
        return Result.success(orderService.deleteOrder(userId, orderNo));
    }

    @GetMapping("/ping")
    @Operation(summary = "健康检查")
    public Result<String> ping() {
        return Result.success("order-service is running");
    }
}

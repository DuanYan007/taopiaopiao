package com.duanyan.taopiaopiao.orderservice.application.controller;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.api.dto.CreatePendingOrderRequest;
import com.duanyan.taopiaopiao.orderservice.api.dto.OrderResponse;
import com.duanyan.taopiaopiao.orderservice.application.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 内部订单控制器。
 */
@Slf4j
@Tag(name = "内部订单管理", description = "内部订单接口")
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @PostMapping("/create-pending")
    @Operation(summary = "创建待支付订单")
    public Result<OrderResponse> createPendingOrder(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                    @Valid @RequestBody CreatePendingOrderRequest request) {
        OrderResponse response = orderService.createPendingOrder(request, requestId);
        return Result.success(response);
    }
}

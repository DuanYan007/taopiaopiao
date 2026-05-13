package com.duanyan.taopiaopiao.orderservice.application.controller;

import com.duanyan.taopiaopiao.common.dto.PrepareOrderRequest;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.application.support.RuntimeTestHookSupport;
import com.duanyan.taopiaopiao.orderservice.application.tcc.OrderTccAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部订单管理", description = "内部订单接口")
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderTccAction orderTccAction;
    private final RuntimeTestHookSupport runtimeTestHookSupport;

    @PostMapping("/prepare")
    @Operation(summary = "TCC Try 阶段预留建单资源")
    public Result<Boolean> prepare(@Valid @RequestBody PrepareOrderRequest request) {
        return Result.success(orderTccAction.tryPrepareOrder(
                null,
                request.getOrderNo(),
                request.getUserId(),
                request.getSessionId(),
                request.getEventId(),
                request.getSeatIds(),
                request.getSeatCount(),
                request.getUnitPrice(),
                request.getTotalAmount(),
                request.getExpireTime()
        ));
    }

    @PostMapping("/test/timeout-delay")
    @Operation(summary = "测试用：延迟下一次 TIMEOUT_CHECK 消费")
    public Result<Boolean> armTimeoutDelay(@RequestParam String orderNo,
                                           @RequestParam Long delayMs) {
        if (!runtimeTestHookSupport.isEnabled()) {
            throw new IllegalStateException("Runtime test hooks are disabled");
        }
        runtimeTestHookSupport.armTimeoutCheckDelay(orderNo, delayMs == null ? 0L : delayMs);
        return Result.success(true);
    }
}

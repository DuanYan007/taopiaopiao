package com.duanyan.taopiaopiao.seckillservice.application.controller;

import com.duanyan.taopiaopiao.common.dto.CancelOrderRequest;
import com.duanyan.taopiaopiao.common.dto.ConfirmOrderRequest;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.application.service.impl.SeckillServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "内部锁单管理", description = "内部锁单接口")
@RestController
@RequestMapping("/internal/lock-orders")
@RequiredArgsConstructor
public class InternalLockOrderController {

    private final SeckillServiceImpl seckillService;

    @PostMapping("/confirm")
    @Operation(summary = "确认订单并售出座位")
    public Result<Boolean> confirmOrder(@RequestBody ConfirmOrderRequest request) {
        return Result.success(seckillService.confirmOrder(
                request.getOrderNo(),
                request.getSessionId(),
                request.getUserId(),
                request.getSeatIds()
        ));
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消订单并释放座位")
    public Result<Boolean> cancelOrder(@RequestBody CancelOrderRequest request) {
        return Result.success(seckillService.cancelOrder(
                request.getOrderNo(),
                request.getSessionId(),
                request.getUserId(),
                request.getSeatIds(),
                request.getReason()
        ));
    }
}

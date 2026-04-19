package com.duanyan.taopiaopiao.seckillservice.application.controller;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.api.dto.InternalLockOrderResponse;
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

    @GetMapping("/{orderNo}")
    @Operation(summary = "查询锁单")
    public Result<InternalLockOrderResponse> getLockOrder(@PathVariable String orderNo,
                                                          @RequestParam Long userId) {
        return Result.success(seckillService.getLockOrder(orderNo, userId));
    }
}

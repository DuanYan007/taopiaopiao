package com.duanyan.taopiaopiao.seckillservice.application.controller;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.application.service.impl.SeckillServiceImpl;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内部座位控制器（供其他服务调用）
 */
@Slf4j
@Tag(name = "内部座位管理", description = "内部座位接口")
@RestController
@RequestMapping("/internal/seats")
@RequiredArgsConstructor
public class InternalSeatController {

    private final SeckillServiceImpl seckillService;

    @PostMapping("/mark-paid")
    @Operation(summary = "标记座位锁定已支付（内部接口）")
    public Result<Integer> markSeatLocksPaid(@RequestParam String orderNo,
                                              @RequestParam Long sessionId,
                                              @RequestParam Long userId,
                                              @RequestParam String lockId,
                                              @RequestParam List<String> seatIds) {
        return Result.success(seckillService.markSeatLocksPaid(orderNo, sessionId, userId, lockId, seatIds));
    }

    @PostMapping("/release")
    @Operation(summary = "释放座位（内部接口，用于取消/超时订单）")
    public Result<String> releaseSeats(@RequestParam Long sessionId,
                                        @RequestParam Long userId,
                                        @RequestParam String lockId,
                                        @RequestParam List<String> seatIds) {
        seckillService.releaseSeats(sessionId, userId, lockId, seatIds, LockStatus.RELEASED);
        return Result.success("释放成功");
    }
}

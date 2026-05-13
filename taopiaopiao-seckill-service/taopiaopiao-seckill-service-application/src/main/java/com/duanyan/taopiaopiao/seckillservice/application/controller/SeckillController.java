package com.duanyan.taopiaopiao.seckillservice.application.controller;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.common.exception.BusinessException;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionLayoutResponse;
import com.duanyan.taopiaopiao.seckillservice.application.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 选座控制器（对外接口）
 */
@Slf4j
@Tag(name = "选座/秒杀", description = "座位锁定接口")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @PostMapping("/lock")
    @Operation(summary = "锁定座位")
    public Result<LockSeatResponse> lockSeats(@RequestHeader("X-User-Id") Long userId,
                                              @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                              @Valid @RequestBody LockSeatRequest request) {
        if (userId == null) {
            throw new BusinessException(400, "请求头 X-User-Id 不能为空");
        }

        String finalRequestId = StringUtils.hasText(requestId)
                ? requestId
                : "srv-" + UUID.randomUUID().toString().replace("-", "");

        return Result.success(seckillService.lockSeats(request, userId, finalRequestId));
    }

    @PostMapping("/init")
    @Operation(summary = "初始化场次缓存（手动调用）")
    public Result<SessionInitResponse> initSession(@Valid @RequestBody SessionInitRequest request) {
        SessionInitResponse response = seckillService.initSession(request);
        return Result.success(response);
    }

    @GetMapping("/{sessionId}/layout")
    @Operation(summary = "获取场次座位布局（含状态）")
    public Result<SessionLayoutResponse> getLayout(
            @Parameter(description = "场次ID", required = true)
            @PathVariable Long sessionId) {
        SessionLayoutResponse response = seckillService.getLayout(sessionId);
        return Result.success(response);
    }

}

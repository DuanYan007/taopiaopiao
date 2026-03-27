package com.duanyan.taopiaopiao.sessionservice.application.controller;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionPageResponse;
import com.duanyan.taopiaopiao.sessionservice.application.client.dto.MarkSeatsSoldRequest;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionQueryRequest;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionResponse;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionSeatsResponse;
import com.duanyan.taopiaopiao.sessionservice.application.service.ClientSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 场次客户端控制器
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/client/sessions")
@RequiredArgsConstructor
@Tag(name = "场次-客户端", description = "客户端场次查询接口")
public class ClientSessionController {

    private final ClientSessionService clientSessionService;

    /**
     * 分页查询场次列表（客户端）
     */
    @GetMapping
    @Operation(summary = "分页查询场次列表（客户端）")
    public Result<SessionPageResponse> getSessionPage(SessionQueryRequest request) {
        SessionPageResponse response = clientSessionService.getSessionPage(request);
        return Result.success(response);
    }

    /**
     * 根据ID查询场次详情（客户端）
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询场次详情（客户端）")
    public Result<SessionResponse> getSessionById(
            @Parameter(description = "场次ID", required = true)
            @PathVariable Long id) {
        SessionResponse response = clientSessionService.getSessionById(id);
        return Result.success(response);
    }

    /**
     * 获取场次座位列表
     */
    @GetMapping("/{id}/seats")
    @Operation(summary = "获取场次座位列表")
    public Result<SessionSeatsResponse> getSessionSeats(
            @Parameter(description = "场次ID", required = true)
            @PathVariable Long id) {
        SessionSeatsResponse response = clientSessionService.getSessionSeats(id);
        return Result.success(response);
    }

    /**
     * 标记座位已售出（内部接口，供订单服务调用）
     */
    @PostMapping("/seats/mark-sold")
    @Operation(summary = "标记座位已售出（内部接口）")
    public Result<Integer> markSeatsSold(@Valid @RequestBody MarkSeatsSoldRequest request) {
        log.info("收到标记座位已售出请求: {}", request);
        Integer updated = clientSessionService.markSeatsSold(
                request.getSessionId(),
                request.getSeatIds(),
                request.getOrderNo()
        );
        return Result.success(updated);
    }

    /**
     * 查询单个座位价格（内部接口，供秒杀服务调用）
     */
    @GetMapping("/{sessionId}/seats/{seatNumber}/price")
    @Operation(summary = "查询单个座位价格（内部接口）")
    public Result<java.math.BigDecimal> getSeatPrice(
            @Parameter(description = "场次ID", required = true)
            @PathVariable Long sessionId,
            @Parameter(description = "座位号", required = true)
            @PathVariable String seatNumber) {
        java.math.BigDecimal price = clientSessionService.getSeatPrice(sessionId, seatNumber);
        return Result.success(price);
    }
}

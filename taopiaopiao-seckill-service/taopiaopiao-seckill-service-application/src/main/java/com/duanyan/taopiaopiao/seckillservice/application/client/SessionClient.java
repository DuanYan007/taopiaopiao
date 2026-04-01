package com.duanyan.taopiaopiao.seckillservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.SessionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 场次服务客户端
 */
@FeignClient(name = "session-service", path = "/client/sessions")
public interface SessionClient {

    /**
     * 根据ID查询场次详情
     */
    @GetMapping("/{id}")
    Result<SessionResponse> getSessionById(@PathVariable("id") Long id);

    /**
     * 查询单个座位价格（内部接口，用于价格校验）
     *
     * @param sessionId 场次ID
     * @param seatNumber 座位号
     * @return 座位价格
     */
    @GetMapping("/{sessionId}/seats/{seatNumber}/price")
    Result<java.math.BigDecimal> getSeatPrice(
            @PathVariable("sessionId") Long sessionId,
            @PathVariable("seatNumber") String seatNumber);
}

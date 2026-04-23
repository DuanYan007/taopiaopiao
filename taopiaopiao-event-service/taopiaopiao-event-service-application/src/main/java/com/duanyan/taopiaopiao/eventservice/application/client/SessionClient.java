package com.duanyan.taopiaopiao.eventservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.eventservice.application.client.dto.SessionPageResponse;
import com.duanyan.taopiaopiao.eventservice.application.client.dto.SessionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 场次服务Feign Client（客户端调用）
 *
 * @author duanyan
 * @since 1.0.0
 */
@FeignClient(name = "session-service", path = "/client/sessions", configuration = org.springframework.cloud.openfeign.FeignClientsConfiguration.class)
public interface SessionClient {

    /**
     * 分页查询场次列表（客户端）
     */
    @GetMapping
    Result<SessionPageResponse> getSessionPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize);

    /**
     * 根据ID查询场次详情（客户端）
     */
    @GetMapping("/{id}")
    Result<SessionResponse> getSessionById(@PathVariable("id") Long id);
}

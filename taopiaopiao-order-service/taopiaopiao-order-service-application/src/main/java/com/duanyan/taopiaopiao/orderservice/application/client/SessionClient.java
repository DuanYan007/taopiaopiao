package com.duanyan.taopiaopiao.orderservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.SessionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 场次服务客户端
 */
@FeignClient(name = "session-service", path = "/client/sessions")
public interface SessionClient {

    @GetMapping("/{id}")
    Result<SessionResponse> getSessionById(@PathVariable("id") Long id);
}

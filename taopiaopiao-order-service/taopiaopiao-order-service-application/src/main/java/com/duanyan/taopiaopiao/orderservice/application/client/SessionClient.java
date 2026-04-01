package com.duanyan.taopiaopiao.orderservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.MarkSeatsSoldRequest;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.SessionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 场次服务客户端
 */
@FeignClient(name = "session-service", path = "/client/sessions")
public interface SessionClient {

    @GetMapping("/{id}")
    Result<SessionResponse> getSessionById(@PathVariable("id") Long id);

    @PostMapping("/seats/mark-sold")
    Result<Integer> markSeatsSold(@RequestBody MarkSeatsSoldRequest request);
}

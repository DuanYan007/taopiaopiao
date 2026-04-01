package com.duanyan.taopiaopiao.sessionservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.sessionservice.application.client.dto.EventResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 演出服务Feign Client
 */
@FeignClient(name = "event-service", path = "/admin/events")
public interface EventClient {

    @GetMapping("/{id}")
    Result<EventResponse> getEventById(@PathVariable("id") Long id);
}

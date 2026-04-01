package com.duanyan.taopiaopiao.sessionservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.sessionservice.application.client.dto.SeatTemplateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 座位模板服务Feign Client
 */
@FeignClient(name = "seat-template-service", path = "/admin/seat-templates")
public interface SeatTemplateClient {

    @GetMapping("/{id}")
    Result<SeatTemplateResponse> getTemplateById(@PathVariable("id") Long id);
}

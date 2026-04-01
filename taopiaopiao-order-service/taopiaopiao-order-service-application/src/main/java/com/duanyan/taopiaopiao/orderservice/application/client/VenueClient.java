package com.duanyan.taopiaopiao.orderservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.VenueResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 场馆服务客户端
 */
@FeignClient(name = "venue-service", path = "/client/venues")
public interface VenueClient {

    @GetMapping("/{id}")
    Result<VenueResponse> getVenueById(@PathVariable("id") Long id);
}

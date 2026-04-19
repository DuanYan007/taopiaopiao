package com.duanyan.taopiaopiao.orderservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.api.dto.InternalLockOrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "seckill-service", path = "/internal/lock-orders")
public interface SeckillInternalClient {

    @GetMapping("/{orderNo}")
    Result<InternalLockOrderResponse> getLockOrder(@PathVariable("orderNo") String orderNo,
                                                   @RequestParam("userId") Long userId);
}

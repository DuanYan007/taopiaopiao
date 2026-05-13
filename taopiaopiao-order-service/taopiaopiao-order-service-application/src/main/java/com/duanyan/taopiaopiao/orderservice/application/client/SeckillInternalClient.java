package com.duanyan.taopiaopiao.orderservice.application.client;

import com.duanyan.taopiaopiao.common.dto.CancelOrderRequest;
import com.duanyan.taopiaopiao.common.dto.ConfirmOrderRequest;
import com.duanyan.taopiaopiao.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "seckill-service", path = "/internal/lock-orders")
public interface SeckillInternalClient {

    @PostMapping("/confirm")
    Result<Boolean> confirmOrder(@RequestBody ConfirmOrderRequest request);

    @PostMapping("/cancel")
    Result<Boolean> cancelOrder(@RequestBody CancelOrderRequest request);
}

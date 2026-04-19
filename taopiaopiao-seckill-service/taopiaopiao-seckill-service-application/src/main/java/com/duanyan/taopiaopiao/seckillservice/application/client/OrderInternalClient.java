package com.duanyan.taopiaopiao.seckillservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderInternalClient {

    @GetMapping("/{orderNo}/exists")
    Result<Boolean> exists(@PathVariable("orderNo") String orderNo);
}

package com.duanyan.taopiaopiao.seckillservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.CreatePendingOrderRequest;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.OrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 订单服务客户端
 */
@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderClient {

    /**
     * 创建待支付订单（内部接口，供秒杀服务调用）
     */
    @PostMapping("/create-pending")
    Result<OrderResponse> createPendingOrder(@RequestBody CreatePendingOrderRequest request);
}

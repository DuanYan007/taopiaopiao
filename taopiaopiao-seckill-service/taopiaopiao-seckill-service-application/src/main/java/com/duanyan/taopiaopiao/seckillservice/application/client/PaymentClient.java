package com.duanyan.taopiaopiao.seckillservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.PaymentCreateRequest;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.PaymentCreateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 支付系统客户端
 */
@FeignClient(
        name = "payment-system",
        url = "http://localhost:7500"
)
public interface PaymentClient {

    /**
     * 创建支付订单
     *
     * @param request 支付创建请求
     * @return 支付创建响应
     */
    @PostMapping("/payment/create")
    Result<PaymentCreateResponse> createPayment(@RequestBody PaymentCreateRequest request);

    /**
     * 查询支付状态
     *
     * @param orderNo 业务订单号
     * @return 支付状态
     */
    @GetMapping("/payment/query")
    Result<com.duanyan.taopiaopiao.seckillservice.application.client.dto.PaymentQueryResponse> queryPayment(@RequestParam("orderNo") String orderNo);
}

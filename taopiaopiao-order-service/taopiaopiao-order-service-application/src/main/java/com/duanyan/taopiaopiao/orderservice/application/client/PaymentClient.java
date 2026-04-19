package com.duanyan.taopiaopiao.orderservice.application.client;

import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentCreateRequest;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentCreateResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentQueryResponse;
import com.duanyan.taopiaopiao.orderservice.application.client.dto.PaymentResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 支付系统客户端
 *
 * @author duanyan
 * @since 1.0.0
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
    PaymentResult<PaymentCreateResponse> createPayment(@RequestBody PaymentCreateRequest request);

    /**
     * 查询支付状态
     *
     * @param orderNo 业务订单号
     * @return 支付状态
     */
    @GetMapping("/payment/query")
    PaymentResult<PaymentQueryResponse> queryPayment(@RequestParam("orderNo") String orderNo);

}

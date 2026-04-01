package com.duanyan.taopiaopiao.sessionservice.application.client;

import com.duanyan.taopiaopiao.sessionservice.application.client.dto.PaymentQueryResponse;
import com.duanyan.taopiaopiao.sessionservice.application.client.dto.PaymentResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 支付系统客户端（场次服务）
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
     * 查询支付状态
     *
     * @param orderNo 业务订单号
     * @return 支付状态
     */
    @GetMapping("/payment/query")
    PaymentResult<PaymentQueryResponse> queryPayment(@RequestParam("orderNo") String orderNo);
}

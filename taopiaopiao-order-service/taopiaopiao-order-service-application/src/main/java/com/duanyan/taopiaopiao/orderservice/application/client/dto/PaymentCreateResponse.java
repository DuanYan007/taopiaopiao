package com.duanyan.taopiaopiao.orderservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付创建响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreateResponse {

    private String orderNo;

    private String paymentNo;

    private String amount;

    private String payUrl;

    private String qrCode;
}

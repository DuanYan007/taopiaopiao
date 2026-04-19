package com.duanyan.taopiaopiao.orderservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付创建请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreateRequest {

    private String orderNo;

    private BigDecimal amount;

    private String payMethod;

    private String body;
}

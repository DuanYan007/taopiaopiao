package com.duanyan.taopiaopiao.orderservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建支付请求
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreateRequest {

    /**
     * 业务订单号
     */
    private String orderNo;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 支付方式
     */
    private String payMethod;

    /**
     * 商品描述
     */
    private String body;

    /**
     * 异步通知地址
     */
    private String notifyUrl;

    /**
     * 同步跳转地址
     */
    private String returnUrl;

    /**
     * 客户端IP
     */
    private String clientIp;
}

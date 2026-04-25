package com.duanyan.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建支付请求
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
public class PaymentCreateRequest {

    /**
     * 业务订单号
     */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /**
     * 支付金额
     */
    @NotNull(message = "支付金额不能为空")
    @Positive(message = "支付金额必须大于0")
    private BigDecimal amount;

    /**
     * 支付方式
     */
    private String payMethod = "MOCK";

    /**
     * 商品描述
     */
    private String body;

    /**
     * 回调地址（仅用于记录，支付系统不主动通知）
     * 业务系统通过 RocketMQ 回查机制主动查询支付状态
     */
    private String notifyUrl;

    /**
     * 前端跳转地址（仅用于记录，实际跳转由前端控制）
     */
    private String returnUrl;

    /**
     * 客户端IP
     */
    private String clientIp;
}

package com.duanyan.taopiaopiao.sessionservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付状态查询响应
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQueryResponse {

    /**
     * 业务订单号
     */
    private String orderNo;

    /**
     * 支付流水号
     */
    private String paymentNo;

    /**
     * 支付状态：PENDING/SUCCESS/FAILED/CANCELLED/NOT_FOUND
     */
    private String status;

    /**
     * 支付状态描述
     */
    private String statusDesc;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 第三方交易号
     */
    private String transactionId;

    /**
     * 支付方式
     */
    private String payMethod;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 支付时间
     */
    private LocalDateTime paidAt;

    /**
     * 判断是否支付成功
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    /**
     * 判断是否待支付
     */
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    /**
     * 判断是否未找到
     */
    public boolean isNotFound() {
        return "NOT_FOUND".equals(status);
    }
}

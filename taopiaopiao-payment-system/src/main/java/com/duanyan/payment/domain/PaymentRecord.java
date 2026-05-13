package com.duanyan.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecord {

    private Long id;

    /**
     * 业务订单号（来自订单服务）
     */
    private String orderNo;

    /**
     * 支付流水号（支付系统生成）
     */
    private String paymentNo;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 状态：PENDING/SUCCESS/FAILED/CANCELLED
     */
    private String status;

    /**
     * 第三方交易号（模拟支付宝/微信）
     */
    private String transactionId;

    /**
     * 支付方式：ALIPAY/WECHAT/MOCK
     */
    private String payMethod;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 前端跳转地址（仅用于记录）
     */
    private String returnUrl;

    /**
     * 回调地址（仅用于记录，支付系统不主动通知）
     */
    private String notifyUrl;

    /**
     * 商品描述
     */
    private String body;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 支付时间
     */
    private LocalDateTime paidAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 获取支付状态枚举
     */
    public PaymentStatus getStatusEnum() {
        return PaymentStatus.fromCode(this.status);
    }

    /**
     * 设置支付状态
     */
    public void setStatus(PaymentStatus status) {
        this.status = status != null ? status.getCode() : null;
    }

    /**
     * 判断是否已支付
     */
    public boolean isPaid() {
        return PaymentStatus.SUCCESS.getCode().equals(this.status);
    }
}

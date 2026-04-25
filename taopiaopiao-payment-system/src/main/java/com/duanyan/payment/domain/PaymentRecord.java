package com.duanyan.payment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务订单号（来自订单服务）
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 支付流水号（支付系统生成）
     */
    @TableField("payment_no")
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
    @TableField("transaction_id")
    private String transactionId;

    /**
     * 支付方式：ALIPAY/WECHAT/MOCK
     */
    @TableField("pay_method")
    private String payMethod;

    /**
     * 客户端IP
     */
    @TableField("client_ip")
    private String clientIp;

    /**
     * 前端跳转地址（仅用于记录）
     */
    @TableField("return_url")
    private String returnUrl;

    /**
     * 回调地址（仅用于记录，支付系统不主动通知）
     */
    @TableField("notify_url")
    private String notifyUrl;

    /**
     * 商品描述
     */
    private String body;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 支付时间
     */
    @TableField("paid_at")
    private LocalDateTime paidAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
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

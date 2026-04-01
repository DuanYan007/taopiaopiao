package com.duanyan.taopiaopiao.seckillservice.application.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 支付状态查询响应
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
    private String transactionId;

    /**
     * 支付状态: PENDING, SUCCESS, FAILED, CANCELLED
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 支付金额
     */
    private String amount;

    /**
     * 支付时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
     * 判断是否支付记录不存在
     */
    public boolean isNotFound() {
        return transactionId == null || transactionId.isEmpty();
    }
}

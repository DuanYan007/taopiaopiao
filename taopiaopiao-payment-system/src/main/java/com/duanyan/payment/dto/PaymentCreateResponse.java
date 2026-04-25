package com.duanyan.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建支付响应
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreateResponse {

    /**
     * 业务订单号
     */
    private String orderNo;

    /**
     * 支付流水号
     */
    private String paymentNo;

    /**
     * 支付金额
     */
    private String amount;

    /**
     * 支付页面地址（模拟）
     */
    private String payUrl;

    /**
     * 支付二维码内容（模拟扫码支付）
     */
    private String qrCode;
}

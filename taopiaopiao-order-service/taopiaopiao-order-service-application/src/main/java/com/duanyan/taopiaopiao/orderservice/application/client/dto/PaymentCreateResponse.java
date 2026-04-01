package com.duanyan.taopiaopiao.orderservice.application.client.dto;

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
     * 支付页面地址
     */
    private String payUrl;

    /**
     * 支付二维码内容
     */
    private String qrCode;
}

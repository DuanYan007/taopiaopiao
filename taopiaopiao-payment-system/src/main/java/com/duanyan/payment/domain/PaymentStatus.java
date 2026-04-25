package com.duanyan.payment.domain;

import lombok.Getter;

/**
 * 支付状态枚举
 *
 * @author duanyan
 * @since 1.0.0
 */
@Getter
public enum PaymentStatus {

    /**
     * 待支付
     */
    PENDING("PENDING", "待支付"),

    /**
     * 支付成功
     */
    SUCCESS("SUCCESS", "支付成功"),

    /**
     * 支付失败
     */
    FAILED("FAILED", "支付失败"),

    /**
     * 已取消
     */
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    PaymentStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PaymentStatus fromCode(String code) {
        for (PaymentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}

package com.duanyan.taopiaopiao.orderservice.domain.enums;

import lombok.Getter;

/**
 * 订单状态
 */
@Getter
public enum OrderStatus {
    PROCESSING(0, "处理中"),
    UNPAID(1, "未支付"),
    PAID(2, "已支付"),
    CANCELLED(3, "已取消"),
    REFUNDED(4, "已退款"),
    TIMEOUT(5, "超时取消");

    private final Integer code;
    private final String desc;

    OrderStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus fromCode(Integer code) {
        for (OrderStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}

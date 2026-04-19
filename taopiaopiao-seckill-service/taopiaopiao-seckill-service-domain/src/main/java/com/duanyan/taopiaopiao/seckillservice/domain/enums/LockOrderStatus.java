package com.duanyan.taopiaopiao.seckillservice.domain.enums;

import lombok.Getter;

@Getter
public enum LockOrderStatus {
    LOCKED(1, "已锁定"),
    ORDER_CREATING(2, "订单创建中"),
    ORDER_CREATED(3, "订单已创建"),
    PAID(4, "已支付"),
    TIMEOUT(5, "超时取消"),
    CANCELLED(6, "已取消"),
    FAILED(7, "失败");

    private final Integer code;
    private final String desc;

    LockOrderStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

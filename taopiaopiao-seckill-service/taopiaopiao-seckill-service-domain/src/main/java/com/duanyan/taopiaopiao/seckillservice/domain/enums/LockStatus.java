package com.duanyan.taopiaopiao.seckillservice.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 锁座状态枚举
 */
@Getter
@AllArgsConstructor
public enum LockStatus {
    RELEASED(0, "已释放"),
    LOCKED(1, "已锁定"),
    PAID(2, "已支付"),
    EXPIRED(3, "已超时释放");

    private final Integer code;
    private final String desc;
}

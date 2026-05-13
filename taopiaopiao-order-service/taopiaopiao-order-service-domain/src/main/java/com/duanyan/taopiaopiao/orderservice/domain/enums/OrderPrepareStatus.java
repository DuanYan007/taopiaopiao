package com.duanyan.taopiaopiao.orderservice.domain.enums;

import lombok.Getter;

@Getter
public enum OrderPrepareStatus {
    PREPARED(0, "已预留"),
    CONFIRMED(1, "已确认"),
    CANCELED(2, "已取消");

    private final Integer code;
    private final String desc;

    OrderPrepareStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

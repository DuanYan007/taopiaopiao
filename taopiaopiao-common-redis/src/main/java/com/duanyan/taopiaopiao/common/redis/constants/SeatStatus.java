package com.duanyan.taopiaopiao.common.redis.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 座位状态枚举
 *
 * @author duanyan
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum SeatStatus {

    /**
     * 可选
     */
    AVAILABLE(0, "可选"),

    /**
     * 已售出
     */
    SOLD(2, "已售出");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 描述
     */
    private final String desc;

}

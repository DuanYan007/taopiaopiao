package com.duanyan.taopiaopiao.common.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 订单取消消息
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class    OrderCancelMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单号（作为消息 Key）
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 场次ID
     */
    private Long sessionId;

    /**
     * 座位ID列表
     */
    private List<String> seatIds;

    /**
     * 取消原因：USER=用户取消, TIMEOUT=超时取消
     */
    private String reason;
}

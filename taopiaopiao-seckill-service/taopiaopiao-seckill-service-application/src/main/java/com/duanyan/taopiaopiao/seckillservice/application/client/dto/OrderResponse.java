package com.duanyan.taopiaopiao.seckillservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    /**
     * 订单号
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
     * 演出ID
     */
    private Long eventId;

    /**
     * 座位ID列表
     */
    private List<String> seatIds;

    /**
     * 座位数量
     */
    private Integer seatCount;

    /**
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    /**
     * 订单状态: 1-未支付, 2-已支付, 3-已取消, 4-已退款, 5-超时取消
     */
    private Integer status;

    /**
     * 订单状态描述
     */
    private String statusDesc;
}

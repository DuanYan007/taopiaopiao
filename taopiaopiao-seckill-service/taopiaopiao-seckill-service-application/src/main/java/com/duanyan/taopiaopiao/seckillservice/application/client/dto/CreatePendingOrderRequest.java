package com.duanyan.taopiaopiao.seckillservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建待支付订单请求（秒杀服务调用订单服务）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePendingOrderRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 场次ID
     */
    private Long sessionId;

    /**
     * 锁ID
     */
    private String lockId;

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
     * 订单超时秒数
     */
    private Integer expireSeconds;
}

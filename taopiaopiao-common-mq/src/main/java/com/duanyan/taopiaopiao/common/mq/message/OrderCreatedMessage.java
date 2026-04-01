package com.duanyan.taopiaopiao.common.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单创建消息（事务消息）
 * <p>
 * 用于在订单创建后通知下游服务
 * 支付成功后，消费者处理订单状态更新、座位状态更新等
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedMessage {

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
     * 支付方式
     */
    private String payMethod;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
}

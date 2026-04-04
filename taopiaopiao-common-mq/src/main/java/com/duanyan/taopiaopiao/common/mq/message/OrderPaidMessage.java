package com.duanyan.taopiaopiao.common.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单支付成功消息（事务消息）
 * <p>
 * 只在支付确认成功后提交给下游服务。
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidMessage {

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

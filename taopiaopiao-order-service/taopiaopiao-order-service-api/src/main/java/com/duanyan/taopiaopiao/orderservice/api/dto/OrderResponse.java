package com.duanyan.taopiaopiao.orderservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单响应")
public class OrderResponse {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "场次ID")
    private Long sessionId;

    @Schema(description = "演出ID")
    private Long eventId;

    @Schema(description = "座位ID列表")
    private List<String> seatIds;

    @Schema(description = "座位数量")
    private Integer seatCount;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "总金额")
    private BigDecimal totalAmount;

    @Schema(description = "订单状态: 1-未支付, 2-已支付, 3-已取消, 4-已退款, 5-超时取消")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "支付状态，如 NOT_READY/READY/SUCCESS/PENDING")
    private String paymentStatus;

    @Schema(description = "建议前端下次轮询间隔，单位毫秒；0 表示无需继续轮询")
    private Long nextPollMs;

    @Schema(description = "支付页面地址")
    private String payUrl;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}

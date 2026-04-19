package com.duanyan.taopiaopiao.seckillservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内部锁单查询响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "内部锁单查询响应")
public class InternalLockOrderResponse {

    @Schema(description = "锁ID")
    private String lockId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "请求ID")
    private String requestId;

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

    @Schema(description = "锁单状态")
    private Integer status;

    @Schema(description = "锁单状态描述")
    private String statusDesc;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}

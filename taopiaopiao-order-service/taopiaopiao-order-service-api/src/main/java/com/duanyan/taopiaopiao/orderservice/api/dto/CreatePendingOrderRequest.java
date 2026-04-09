package com.duanyan.taopiaopiao.orderservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建待支付订单请求。
 * <p>
 * 用于秒杀服务发起内部下单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建待支付订单请求")
public class CreatePendingOrderRequest {

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", required = true)
    private Long userId;

    @NotNull(message = "场次ID不能为空")
    @Schema(description = "场次ID", required = true)
    private Long sessionId;

    @NotNull(message = "锁ID不能为空")
    @Schema(description = "锁ID", required = true)
    private String lockId;

    @Schema(description = "演出ID")
    private Long eventId;

    @NotEmpty(message = "座位ID不能为空")
    @Schema(description = "座位ID列表", required = true)
    private List<String> seatIds;

    @NotNull(message = "座位数量不能为空")
    @Schema(description = "座位数量", required = true)
    private Integer seatCount;

    @NotNull(message = "单价不能为空")
    @Schema(description = "单价", required = true)
    private BigDecimal unitPrice;

    @NotNull(message = "总金额不能为空")
    @Schema(description = "订单总金额", required = true)
    private BigDecimal totalAmount;

    @NotNull(message = "订单超时秒数不能为空")
    @Schema(description = "订单超时秒数", required = true)
    private Integer expireSeconds;
}

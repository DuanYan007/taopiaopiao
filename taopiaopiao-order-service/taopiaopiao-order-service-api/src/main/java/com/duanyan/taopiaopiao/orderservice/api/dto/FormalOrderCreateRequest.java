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
 * 正式订单创建参数。
 * <p>
 * 由 order-service 在 TCC Confirm 阶段构造，用于创建本地 UNPAID 正式订单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "正式订单创建参数")
public class FormalOrderCreateRequest {

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", required = true)
    private Long userId;

    @NotNull(message = "场次ID不能为空")
    @Schema(description = "场次ID", required = true)
    private Long sessionId;

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

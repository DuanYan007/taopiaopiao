package com.duanyan.taopiaopiao.orderservice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import org.apache.ibatis.type.JdbcType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "orders", autoResultMap = true)
@Schema(description = "订单")
public class Order {

    @TableId(type = IdType.AUTO)
    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "场次ID")
    private Long sessionId;

    @Schema(description = "锁ID")
    private String lockId;

    @Schema(description = "演出ID")
    private Long eventId;

    @Schema(description = "座位ID列表(JSON)")
    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private String seatIds;

    @Schema(description = "座位数量")
    private Integer seatCount;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "总金额")
    private BigDecimal totalAmount;

    @Schema(description = "订单状态: 1-未支付, 2-已支付, 3-已取消, 4-已退款, 5-超时取消")
    private Integer status;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "取消时间")
    private LocalDateTime cancelTime;

    @Schema(description = "退款时间")
    private LocalDateTime refundTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}

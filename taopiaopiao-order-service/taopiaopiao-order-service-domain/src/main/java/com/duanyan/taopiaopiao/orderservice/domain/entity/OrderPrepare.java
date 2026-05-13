package com.duanyan.taopiaopiao.orderservice.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "order_prepare", autoResultMap = true)
@Schema(description = "TCC 订单预留")
public class OrderPrepare {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String xid;

    private Long userId;

    private Long sessionId;

    private Long eventId;

    @TableField(value = "seat_ids", typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private List<String> seatIds;

    private Integer seatCount;

    private BigDecimal unitPrice;

    private BigDecimal totalAmount;

    private LocalDateTime expireTime;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

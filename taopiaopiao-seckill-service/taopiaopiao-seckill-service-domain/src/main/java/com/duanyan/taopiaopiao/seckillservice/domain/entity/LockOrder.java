package com.duanyan.taopiaopiao.seckillservice.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "lock_orders", autoResultMap = true)
public class LockOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String lockId;
    private String orderNo;
    private String requestId;
    private Long userId;
    private Long sessionId;
    private Long eventId;

    @TableField(value = "seat_ids_json", jdbcType = JdbcType.VARCHAR, typeHandler = JacksonTypeHandler.class)
    private List<String> seatIds;

    private Integer seatCount;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime expireTime;
    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

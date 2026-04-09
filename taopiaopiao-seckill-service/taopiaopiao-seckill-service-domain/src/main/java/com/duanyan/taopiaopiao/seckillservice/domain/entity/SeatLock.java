package com.duanyan.taopiaopiao.seckillservice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 座位锁定记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("seat_locks")
public class SeatLock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long userId;
    private String seatId;
    private String lockId;

    private Integer seatRow;
    private Integer seatCol;

    private Long lockTime;
    private Long expireTime;
    private Integer status;
    private String orderNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

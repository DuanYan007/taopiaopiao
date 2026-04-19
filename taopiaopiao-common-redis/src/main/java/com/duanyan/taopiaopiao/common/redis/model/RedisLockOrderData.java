package com.duanyan.taopiaopiao.common.redis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisLockOrderData implements Serializable {

    private String lockId;
    private String orderNo;
    private String requestId;
    private Long userId;
    private Long sessionId;
    private Long eventId;
    private List<String> seatIds;
    private Integer seatCount;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Integer status;
    private String paymentStatus;
    private String failReason;
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

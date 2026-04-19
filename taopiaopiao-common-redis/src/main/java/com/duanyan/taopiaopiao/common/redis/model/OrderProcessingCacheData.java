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
public class OrderProcessingCacheData implements Serializable {

    private String orderNo;
    private Long userId;
    private Long sessionId;
    private Long eventId;
    private List<String> seatIds;
    private Integer seatCount;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String status;
    private String paymentStatus;
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;
}

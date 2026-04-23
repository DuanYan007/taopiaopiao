package com.duanyan.taopiaopiao.orderservice.application.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * seckill-service 内部锁单查询响应（order-service 本地契约）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalLockOrderResponse {

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

    private String statusDesc;

    private String failReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}

package com.duanyan.taopiaopiao.common.dto;

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
public class PrepareOrderRequest {

    private Long userId;
    private Long sessionId;
    private Long eventId;
    private String orderNo;
    private List<String> seatIds;
    private Integer seatCount;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private LocalDateTime expireTime;
    private String requestId;
}

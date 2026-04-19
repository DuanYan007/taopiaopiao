package com.duanyan.taopiaopiao.common.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 锁座受理事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockAcceptedMessage {

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
    private LocalDateTime expireTime;
}

package com.duanyan.taopiaopiao.common.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 正式订单创建完成后的内部状态同步事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedInternalMessage {

    private String orderNo;
    private String lockId;
    private Long userId;
    private Long sessionId;
    private LocalDateTime createdAt;
}

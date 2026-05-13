package com.duanyan.taopiaopiao.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderRequest {

    private String orderNo;
    private Long userId;
    private Long sessionId;
    private List<String> seatIds;
    private String reason;
}

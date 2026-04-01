package com.duanyan.taopiaopiao.orderservice.application.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场次信息响应（order-service内部使用）
 */
@Data
public class SessionResponse {

    /**
     * 场次ID
     */
    private Long id;

    /**
     * 演出ID
     */
    private Long eventId;

    /**
     * 场次名称
     */
    private String sessionName;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 座位模板ID
     */
    private Long seatTemplateId;
}

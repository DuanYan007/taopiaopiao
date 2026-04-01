package com.duanyan.taopiaopiao.seckillservice.application.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场次响应
 * <p>
 * 注意：价格信息存储在 seats 表中，不在场次表中
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
     * 座位模板ID
     */
    private Long seatTemplateId;

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
     * 场次状态
     */
    private String status;
}

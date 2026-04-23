package com.duanyan.taopiaopiao.eventservice.application.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场次服务响应（event-service 本地客户端契约）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private Long id;

    private Long eventId;

    private String sessionName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Long seatTemplateId;

    private String address;

    private Integer availableSeats;

    private Integer soldSeats;

    private Integer lockedSeats;

    private String status;

    private SessionMetadata metadata;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionMetadata {
        private Integer duration;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime saleStartTime;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime saleEndTime;

        private String seatSelectionMode;

        private Boolean requireRealName;

        private Boolean limitOnePerPerson;

        private Boolean noRefund;

        private Integer sortOrder;

        private String remark;
    }
}

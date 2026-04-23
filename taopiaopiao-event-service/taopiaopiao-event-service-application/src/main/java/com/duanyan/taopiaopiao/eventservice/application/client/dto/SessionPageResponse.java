package com.duanyan.taopiaopiao.eventservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 场次服务分页响应（event-service 本地客户端契约）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionPageResponse {

    private List<SessionResponse> list;

    private Long total;

    private Integer page;

    private Integer pageSize;

    private Integer totalPages;
}

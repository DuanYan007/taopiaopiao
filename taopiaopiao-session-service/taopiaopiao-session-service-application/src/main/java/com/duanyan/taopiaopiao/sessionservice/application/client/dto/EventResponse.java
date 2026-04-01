package com.duanyan.taopiaopiao.sessionservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 演出信息响应（session-service内部使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {

    /**
     * 演出ID
     */
    private Long id;

    /**
     * 演出名称
     */
    private String name;

    /**
     * 封面图片URL
     */
    private String coverImage;
}

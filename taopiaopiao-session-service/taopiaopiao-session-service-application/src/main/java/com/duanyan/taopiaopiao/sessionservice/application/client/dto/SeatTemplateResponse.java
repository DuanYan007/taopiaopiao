package com.duanyan.taopiaopiao.sessionservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 座位模板信息响应（session-service内部使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatTemplateResponse {

    /**
     * 模板ID
     */
    private Long id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 关联场馆ID
     */
    private Long venueId;

    /**
     * 总座位数
     */
    private Integer totalSeats;

    /**
     * 布局类型: 1=普通, 2=VIP分区, 3=混合, 4=自定义
     */
    private Integer layoutType;

    /**
     * 座位布局数据(JSON字符串)
     */
    private String layoutData;
}

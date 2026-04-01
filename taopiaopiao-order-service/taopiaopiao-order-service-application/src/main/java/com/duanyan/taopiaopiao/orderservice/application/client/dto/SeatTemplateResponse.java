package com.duanyan.taopiaopiao.orderservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 座位模板信息响应（order-service内部使用）
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
}

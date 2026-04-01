package com.duanyan.taopiaopiao.orderservice.application.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场馆信息响应（order-service内部使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueResponse {

    /**
     * 场馆ID
     */
    private Long id;

    /**
     * 场馆名称
     */
    private String name;
}

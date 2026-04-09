package com.duanyan.taopiaopiao.orderservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 订单分页响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单分页响应")
public class OrderPageResponse {

    @Schema(description = "订单列表")
    private List<OrderListItem> list;

    @Schema(description = "总数")
    private Long total;

    @Schema(description = "页码")
    private Integer page;

    @Schema(description = "每页大小")
    private Integer pageSize;

    /**
     * 订单列表项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "订单列表项")
    public static class OrderListItem {

        @Schema(description = "订单号")
        private String orderNo;

        @Schema(description = "订单状态: 1-未支付, 2-已支付, 3-已取消, 4-已退款, 5-超时取消")
        private Integer status;

        @Schema(description = "状态描述")
        private String statusDesc;

        @Schema(description = "演出名称")
        private String eventName;

        @Schema(description = "演出封面URL")
        private String eventCover;

        @Schema(description = "演出ID")
        private Long eventId;

        @Schema(description = "开始时间")
        private String startTime;

        @Schema(description = "场馆名称")
        private String venueName;

        @Schema(description = "座位信息")
        private String seatInfo;

        @Schema(description = "座位详情列表")
        private List<SeatDetail> seatDetails;

        @Schema(description = "座位数量")
        private Integer seatCount;

        @Schema(description = "单价")
        private java.math.BigDecimal unitPrice;

        @Schema(description = "总金额")
        private java.math.BigDecimal totalAmount;

        @Schema(description = "创建时间")
        private String createdAt;

        @Schema(description = "支付时间")
        private String payTime;

        @Schema(description = "取消时间")
        private String cancelTime;

        @Schema(description = "退款时间")
        private String refundTime;
    }

    /**
     * 座位详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "座位详情")
    public static class SeatDetail {

        @Schema(description = "区域名称")
        private String areaName;

        @Schema(description = "行号")
        private String rowNum;

        @Schema(description = "座位号")
        private String seatNum;
    }
}

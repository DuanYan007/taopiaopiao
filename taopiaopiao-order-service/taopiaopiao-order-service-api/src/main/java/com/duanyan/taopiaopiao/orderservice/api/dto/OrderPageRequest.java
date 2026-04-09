package com.duanyan.taopiaopiao.orderservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单分页查询请求
 */
@Data
@Schema(description = "订单分页查询请求")
public class OrderPageRequest {

    @Schema(description = "订单状态: 1-未支付, 2-已支付, 3-已取消, 4-已退款, 5-超时取消")
    private Integer status;

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;
}

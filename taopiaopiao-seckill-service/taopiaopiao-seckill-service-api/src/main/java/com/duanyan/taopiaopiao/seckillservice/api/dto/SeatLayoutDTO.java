package com.duanyan.taopiaopiao.seckillservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 座位布局信息
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "座位布局信息")
public class SeatLayoutDTO {

    @Schema(description = "座位ID", required = true)
    private Long id;

    @Schema(description = "行号", required = true)
    private Integer row;

    @Schema(description = "列号", required = true)
    private Integer col;

    @Schema(description = "状态: 0-可选, 1-已下单未支付, 2-已售出", required = true)
    private Integer status;
}

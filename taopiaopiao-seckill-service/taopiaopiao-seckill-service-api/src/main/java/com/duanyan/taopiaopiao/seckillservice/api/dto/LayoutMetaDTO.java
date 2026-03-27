package com.duanyan.taopiaopiao.seckillservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 座位布局元数据
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "座位布局元数据")
public class LayoutMetaDTO {

    @Schema(description = "总座位数")
    private Integer totalSeats;

    @Schema(description = "总区域数")
    private Integer totalAreas;

    @Schema(description = "区域名称列表")
    private List<String> areaNames;

    @Schema(description = "各区域价格列表")
    private List<Integer> areaPrices;
}

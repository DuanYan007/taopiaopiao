package com.duanyan.taopiaopiao.seckillservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 座位初始化项
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "座位初始化项")
public class SeatInitItem {

    @NotNull(message = "座位ID不能为空")
    @Schema(description = "座位ID（数据库主键）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotNull(message = "行号不能为空")
    @Schema(description = "行号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer row;

    @NotNull(message = "列号不能为空")
    @Schema(description = "列号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer col;

    @NotNull(message = "区域索引不能为空")
    @Schema(description = "区域索引（对应areaNames的索引位置）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer areaIndex;
}

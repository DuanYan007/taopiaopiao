package com.duanyan.taopiaopiao.seckillservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 场次缓存初始化请求
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "场次缓存初始化请求")
public class SessionInitRequest {

    @NotNull(message = "场次ID不能为空")
    @Schema(description = "场次ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sessionId;

    @NotEmpty(message = "区域名称不能为空")
    @Schema(description = "区域名称列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"VIP区\", \"A区\", \"B区\"]")
    private List<String> areaNames;

    @NotEmpty(message = "区域价格不能为空")
    @Schema(description = "各区域价格列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[380, 280, 280]")
    private List<Integer> areaPrices;

    @NotEmpty(message = "座位列表不能为空")
    @Schema(description = "座位列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SeatInitItem> seats;
}

package com.duanyan.taopiaopiao.seckillservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 场次座位布局响应
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "场次座位布局响应")
public class SessionLayoutResponse {

    @Schema(description = "场次ID")
    private Long sessionId;

    @Schema(description = "元数据")
    private LayoutMetaDTO meta;

    @Schema(description = "各区域座位列表")
    private List<List<SeatLayoutDTO>> areas;
}

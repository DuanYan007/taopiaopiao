package com.duanyan.taopiaopiao.seckillservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场次缓存初始化响应
 *
 * @author duanyan
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "场次缓存初始化响应")
public class SessionInitResponse {

    @Schema(description = "场次ID")
    private Long sessionId;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "消息")
    private String message;

    @Schema(description = "初始化的座位总数")
    private Integer totalSeats;

    @Schema(description = "区域总数")
    private Integer totalAreas;

    @Schema(description = "执行耗时（毫秒）")
    private Long executedTime;
}

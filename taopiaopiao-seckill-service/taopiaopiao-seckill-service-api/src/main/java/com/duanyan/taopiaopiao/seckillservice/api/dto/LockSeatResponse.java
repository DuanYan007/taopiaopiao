package com.duanyan.taopiaopiao.seckillservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 锁座响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "锁座响应")
public class LockSeatResponse {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "状态码: 0=成功, 1=座位不存在, 2=座位不可用, 3=重复购票")
    private Integer code;

    @Schema(description = "消息")
    private String message;

    @Schema(description = "锁定的座位ID列表")
    private List<String> lockedSeats;

    @Schema(description = "锁定ID")
    private String lockId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "订单状态，如 PROCESSING/UNPAID/PAID")
    private String orderStatus;

    @Schema(description = "支付状态，如 NOT_READY/READY/SUCCESS")
    private String paymentStatus;

    @Schema(description = "建议前端下次轮询间隔，单位毫秒；0 表示无需继续轮询")
    private Long nextPollMs;

    @Schema(description = "前端下一步动作，如 POLL_ORDER")
    private String nextAction;

    @Schema(description = "支付页面地址（支付系统返回）")
    private String payUrl;
}

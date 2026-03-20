package com.duanyan.taopiaopiao.seckillservice.application.controller;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatResponse;
import com.duanyan.taopiaopiao.seckillservice.application.service.SeckillService;
import com.duanyan.taopiaopiao.seckillservice.application.service.impl.SeckillServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 选座控制器（对外接口）
 */
@Slf4j
@Tag(name = "选座/秒杀", description = "座位锁定接口")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @PostMapping("/lock")
    @Operation(summary = "锁定座位")
    public Result<LockSeatResponse> lockSeats(@Valid @RequestBody LockSeatRequest request) {
        LockSeatResponse response = seckillService.lockSeats(request);
        return response.getSuccess() ? Result.success(response) : Result.fail(response.getCode(), response.getMessage());
    }

}

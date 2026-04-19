package com.duanyan.taopiaopiao.orderservice.application.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部订单管理", description = "内部订单接口")
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderMapper orderMapper;

    @GetMapping("/{orderNo}/exists")
    @Operation(summary = "判断正式订单是否存在")
    public Result<Boolean> exists(@PathVariable String orderNo) {
        Long count = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)
        );
        return Result.success(count != null && count > 0);
    }
}

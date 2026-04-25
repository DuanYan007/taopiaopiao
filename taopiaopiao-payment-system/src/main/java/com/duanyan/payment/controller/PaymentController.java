package com.duanyan.payment.controller;

import com.duanyan.payment.dto.PaymentCreateRequest;
import com.duanyan.payment.dto.PaymentCreateResponse;
import com.duanyan.payment.dto.PaymentQueryResponse;
import com.duanyan.payment.dto.Result;
import com.duanyan.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 支付接口
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "支付接口", description = "提供支付创建、查询等功能")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 创建支付订单
     *
     * @param request 支付创建请求
     * @return 支付创建响应
     */
    @Operation(summary = "创建支付订单", description = "根据业务订单号创建支付订单，返回支付页面地址")
    @PostMapping("/create")
    public Result<PaymentCreateResponse> createPayment(@Valid @RequestBody PaymentCreateRequest request) {
        log.info("创建支付请求: orderNo={}, amount={}", request.getOrderNo(), request.getAmount());
        PaymentCreateResponse response = paymentService.createPayment(request);
        return Result.success(response);
    }

    /**
     * 查询支付状态
     *
     * @param orderNo 业务订单号
     * @return 支付状态
     */
    @Operation(summary = "查询支付状态", description = "根据业务订单号查询支付状态")
    @GetMapping("/query")
    public Result<PaymentQueryResponse> queryPayment(@RequestParam("orderNo") String orderNo) {
        log.info("查询支付状态: orderNo={}", orderNo);
        PaymentQueryResponse response = paymentService.queryPayment(orderNo);
        return Result.success(response);
    }

    /**
     * 查询支付状态（通过支付流水号）
     *
     * @param paymentNo 支付流水号
     * @return 支付状态
     */
    @Operation(summary = "查询支付状态（通过流水号）", description = "根据支付流水号查询支付状态")
    @GetMapping("/query/{paymentNo}")
    public Result<PaymentQueryResponse> queryByPaymentNo(@PathVariable String paymentNo) {
        log.info("查询支付状态: paymentNo={}", paymentNo);
        // 通过 paymentNo 查询，需要在 Service 中支持
        PaymentQueryResponse response = paymentService.queryPayment(paymentNo);
        return Result.success(response);
    }
}

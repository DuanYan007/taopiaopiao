package com.duanyan.payment.controller;

import com.duanyan.payment.dto.Result;
import com.duanyan.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付模拟接口（测试用）
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "支付模拟接口", description = "用于测试，模拟支付成功/失败等场景")
@RestController
@RequestMapping("/payment/simulate")
@RequiredArgsConstructor
public class PaymentSimulationController {

    private final PaymentService paymentService;

    /**
     * 模拟支付成功
     *
     * @param request 请求参数 { "orderNo": "xxx" }
     * @return 是否成功
     */
    @Operation(summary = "模拟支付成功", description = "测试接口，将订单状态改为支付成功")
    @PostMapping("/success")
    public Result<String> simulateSuccess(@RequestBody Map<String, String> request) {
        String orderNo = request.get("orderNo");
        log.info("模拟支付成功: orderNo={}", orderNo);

        boolean success = paymentService.simulateSuccess(orderNo);

        if (success) {
            return Result.success("支付成功");
        } else {
            return Result.error("订单不存在或状态不允许");
        }
    }

    /**
     * 模拟支付失败
     *
     * @param request 请求参数 { "orderNo": "xxx" }
     * @return 是否成功
     */
    @Operation(summary = "模拟支付失败", description = "测试接口，将订单状态改为支付失败")
    @PostMapping("/fail")
    public Result<String> simulateFail(@RequestBody Map<String, String> request) {
        String orderNo = request.get("orderNo");
        log.info("模拟支付失败: orderNo={}", orderNo);

        boolean success = paymentService.simulateFail(orderNo);

        if (success) {
            return Result.success("支付失败");
        } else {
            return Result.error("订单不存在");
        }
    }

    /**
     * 取消支付
     *
     * @param request 请求参数 { "orderNo": "xxx" }
     * @return 是否成功
     */
    @Operation(summary = "取消支付", description = "将待支付订单取消")
    @PostMapping("/cancel")
    public Result<String> cancelPayment(@RequestBody Map<String, String> request) {
        String orderNo = request.get("orderNo");
        log.info("取消支付: orderNo={}", orderNo);

        boolean success = paymentService.cancelPayment(orderNo);

        if (success) {
            return Result.success("取消成功");
        } else {
            return Result.error("订单不存在或状态不允许");
        }
    }

    /**
     * 模拟支付成功（通过 GET 请求，方便浏览器测试）
     * 返回 HTML 页面而不是 JSON
     *
     * @param orderNo 订单号
     * @return HTML 页面
     */
    @Operation(summary = "模拟支付成功（GET）", description = "方便浏览器直接访问测试")
    @GetMapping(value = "/success", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public String simulateSuccessGet(@RequestParam String orderNo) {
        log.info("模拟支付成功: orderNo={}", orderNo);

        boolean success = paymentService.simulateSuccess(orderNo);

        if (success) {
            return "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<meta charset=\"UTF-8\">" +
                    "<title>支付成功</title>" +
                    "<style>" +
                    "body { font-family: Arial; text-align: center; padding: 50px; }" +
                    "h1 { color: #52c41a; }" +
                    ".info { margin: 20px 0; color: #666; }" +
                    ".btn { padding: 10px 30px; background: #1890ff; color: white; text-decoration: none; border-radius: 4px; display: inline-block; margin-top: 20px; }" +
                    "</style>" +
                    "</head>" +
                    "<body>" +
                    "<h1>✓ 支付成功</h1>" +
                    "<div class=\"info\">订单号：" + orderNo + "</div>" +
                    "<div class=\"info\">支付系统模拟完成，订单状态已更新</div>" +
                    "<p>请关闭此窗口返回业务系统</p>" +
                    "<a href=\"javascript:window.close();\" class=\"btn\">关闭窗口</a>" +
                    "</body>" +
                    "</html>";
        } else {
            return "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<meta charset=\"UTF-8\">" +
                    "<title>支付失败</title>" +
                    "<style>" +
                    "body { font-family: Arial; text-align: center; padding: 50px; }" +
                    "h1 { color: #ff4d4f; }" +
                    "</style>" +
                    "</head>" +
                    "<body>" +
                    "<h1>✗ 支付失败</h1>" +
                    "<p>订单不存在或状态不允许</p>" +
                    "<p>订单号：" + orderNo + "</p>" +
                    "</body>" +
                    "</html>";
        }
    }
}

package com.duanyan.payment.service;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderPaidMessage;
import com.duanyan.payment.domain.PaymentRecord;
import com.duanyan.payment.domain.PaymentStatus;
import com.duanyan.payment.dto.PaymentCreateRequest;
import com.duanyan.payment.dto.PaymentCreateResponse;
import com.duanyan.payment.dto.PaymentQueryResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务实现
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String ORDER_PAID_DESTINATION = MqTopic.ORDER_TOPIC + ":" + MqTopic.TAG_ORDER_PAID;

    private final ConcurrentHashMap<String, PaymentRecord> orderNoStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PaymentRecord> paymentNoStore = new ConcurrentHashMap<>();
    private final RocketMQTemplate rocketMQTemplate;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @PostConstruct
    public void init() {
        log.info("支付系统初始化完成，端口: 7500，存储模式: memory");
    }

    /**
     * 创建支付订单
     *
     * @param request 支付创建请求
     * @return 支付创建响应
     */
    public PaymentCreateResponse createPayment(PaymentCreateRequest request) {
        PaymentRecord record = orderNoStore.computeIfAbsent(request.getOrderNo(), ignored -> {
            PaymentRecord created = PaymentRecord.builder()
                    .orderNo(request.getOrderNo())
                    .paymentNo(generatePaymentNo())
                    .amount(request.getAmount())
                    .status(PaymentStatus.PENDING.getCode())
                    .payMethod(request.getPayMethod())
                    .body(request.getBody())
                    .notifyUrl(request.getNotifyUrl())
                    .returnUrl(request.getReturnUrl())
                    .clientIp(request.getClientIp())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            paymentNoStore.put(created.getPaymentNo(), created);
            return created;
        });

        log.info("创建支付订单成功: orderNo={}, paymentNo={}, amount={}",
                record.getOrderNo(), record.getPaymentNo(), record.getAmount());
        return buildCreateResponse(record);
    }

    /**
     * 查询支付状态
     *
     * @param orderNo 业务订单号
     * @return 支付状态
     */
    public PaymentQueryResponse queryPayment(String orderNo) {
        PaymentRecord record = getRecord(orderNo);
        if (record == null) {
            return PaymentQueryResponse.builder()
                    .orderNo(orderNo)
                    .status("NOT_FOUND")
                    .statusDesc("订单不存在")
                    .build();
        }

        return buildQueryResponse(record);
    }

    /**
     * 模拟支付成功
     *
     * @param orderNo 业务订单号
     * @return 是否成功
     */
    public boolean simulateSuccess(String orderNo) {
        PaymentRecord record = getRecord(orderNo);
        if (record == null) {
            log.warn("订单不存在: {}", orderNo);
            return false;
        }

        if (record.getStatusEnum() == PaymentStatus.SUCCESS) {
            log.warn("订单已是支付成功状态: {}", orderNo);
            return true;
        }

        // 更新状态
        record.setStatus(PaymentStatus.SUCCESS);
        record.setTransactionId("MOCK_" + System.currentTimeMillis());
        record.setPaidAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        publishOrderPaid(record);
        log.info("模拟支付成功: orderNo={}, paymentNo={}", orderNo, record.getPaymentNo());

        return true;
    }

    /**
     * 模拟支付失败
     *
     * @param orderNo 业务订单号
     * @return 是否成功
     */
    public boolean simulateFail(String orderNo) {
        PaymentRecord record = getRecord(orderNo);
        if (record == null) {
            log.warn("订单不存在: {}", orderNo);
            return false;
        }

        record.setStatus(PaymentStatus.FAILED);
        record.setUpdatedAt(LocalDateTime.now());

        log.info("模拟支付失败: orderNo={}", orderNo);

        return true;
    }

    /**
     * 取消支付
     *
     * @param orderNo 业务订单号
     * @return 是否成功
     */
    public boolean cancelPayment(String orderNo) {
        PaymentRecord record = getRecord(orderNo);
        if (record == null) {
            log.warn("订单不存在: {}", orderNo);
            return false;
        }

        // 只有待支付状态才能取消
        if (record.getStatusEnum() != PaymentStatus.PENDING) {
            log.warn("订单状态不是待支付，无法取消: orderNo, status={}", orderNo, record.getStatus());
            return false;
        }

        record.setStatus(PaymentStatus.CANCELLED);
        record.setUpdatedAt(LocalDateTime.now());

        log.info("取消支付: orderNo={}", orderNo);

        return true;
    }

    /**
     * 获取支付记录
     */
    private PaymentRecord getRecord(String orderNo) {
        PaymentRecord record = orderNoStore.get(orderNo);
        if (record != null) {
            return record;
        }
        return paymentNoStore.get(orderNo);
    }

    /**
     * 生成支付流水号
     * 格式: PAY + 时间戳 + 4位随机数
     */
    private String generatePaymentNo() {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "PAY" + timestamp + random;
    }

    private PaymentCreateResponse buildCreateResponse(PaymentRecord record) {
        return PaymentCreateResponse.builder()
                .orderNo(record.getOrderNo())
                .paymentNo(record.getPaymentNo())
                .amount(record.getAmount().toString())
                .payUrl("/payment/simulate/success?orderNo=" + record.getOrderNo())
                .qrCode("mock_qr_code_" + record.getPaymentNo())
                .build();
    }

    private PaymentQueryResponse buildQueryResponse(PaymentRecord record) {
        return PaymentQueryResponse.builder()
                .orderNo(record.getOrderNo())
                .paymentNo(record.getPaymentNo())
                .status(record.getStatus())
                .statusDesc(record.getStatusEnum() != null ? record.getStatusEnum().getDesc() : record.getStatus())
                .amount(record.getAmount())
                .transactionId(record.getTransactionId())
                .payMethod(record.getPayMethod())
                .createdAt(record.getCreatedAt())
                .paidAt(record.getPaidAt())
                .build();
    }

    private void publishOrderPaid(PaymentRecord record) {
        OrderPaidMessage message = OrderPaidMessage.builder()
                .orderNo(record.getOrderNo())
                .seatIds(List.of())
                .seatCount(0)
                .unitPrice(BigDecimal.ZERO)
                .totalAmount(record.getAmount())
                .payMethod(record.getPayMethod())
                .createdAt(record.getCreatedAt())
                .build();
        rocketMQTemplate.syncSend(ORDER_PAID_DESTINATION, message);
        log.info("发送 ORDER_PAID 成功: orderNo={}", record.getOrderNo());
    }

}

package com.duanyan.taopiaopiao.seckillservice.application.service.impl;

import com.duanyan.taopiaopiao.common.exception.BusinessException;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LayoutMetaDTO;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SeatInitItem;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SeatLayoutDTO;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionLayoutResponse;
import com.duanyan.taopiaopiao.seckillservice.application.client.OrderClient;
import com.duanyan.taopiaopiao.seckillservice.application.client.PaymentClient;
import com.duanyan.taopiaopiao.seckillservice.application.client.SessionClient;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.CreatePendingOrderRequest;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.OrderResponse;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.PaymentCreateRequest;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.PaymentCreateResponse;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.SessionResponse;
import com.duanyan.taopiaopiao.seckillservice.application.mapper.SeatLockMapper;
import com.duanyan.taopiaopiao.seckillservice.application.service.SeckillService;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.SeatLock;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeckillServiceImpl implements SeckillService {

    private static final int DEFAULT_ORDER_EXPIRE_SECONDS = 300;
    private static final int LOCK_TTL_GRACE_SECONDS = 30;

    private final RedisService redisService;
    private final SeatLockMapper seatLockMapper;
    private final SessionClient sessionClient;
    private final OrderClient orderClient;
    private final PaymentClient paymentClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public LockSeatResponse lockSeats(LockSeatRequest request, Long userId, String requestId) {
        Long sessionId = request.getSessionId();
        List<String> seatIds = request.getSeatIds();
        BigDecimal frontendUnitPrice = request.getUnitPrice();  // 前端传入的单价
        int orderExpireSeconds = request.getExpireSeconds() != null
                ? request.getExpireSeconds()
                : DEFAULT_ORDER_EXPIRE_SECONDS;
        int lockExpireSeconds = orderExpireSeconds + LOCK_TTL_GRACE_SECONDS;

        String lockId = UUID.randomUUID().toString().replace("-", "");
        long expireTime = System.currentTimeMillis() + lockExpireSeconds * 1000L;

        log.info("开始锁座: requestId={}, sessionId={}, userId={}, seatIds={}",
                requestId, sessionId, userId, seatIds);

        // 1. 锁定座位（Redis）
        int code = redisService.lockSeats(sessionId, userId, lockId, seatIds, lockExpireSeconds);

        if (code == 0) {
            // 插入 seat_locks 记录，orderNo 暂时为空
            for (String seatId : seatIds) {
                SeatLock seatLock = SeatLock.builder()
                        .sessionId(sessionId)
                        .userId(userId)
                        .seatId(seatId)
                        .lockId(lockId)
                        .seatRow(0)
                        .seatCol(0)
                        .lockTime(System.currentTimeMillis())
                        .expireTime(expireTime)
                        .status(LockStatus.LOCKED.getCode())
                        .orderNo(null)
                        .build();
                seatLockMapper.insert(seatLock);
            }
            log.info("锁座成功: requestId={}, sessionId={}, userId={}, lockId={}",
                    requestId, sessionId, userId, lockId);

            // 调用订单服务创建待支付订单
            String orderNo = null;
            String payUrl = null;
            try {
                // 2. 计算总金额（前端价格 × 座位数量）
                List<BigDecimal> prices = redisService.getSeatsPrice(sessionId, seatIds);
                BigDecimal totalAmount = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

                // 3. 获取场次信息（获取 eventId）之后考虑前端是否能删除
                Result<SessionResponse> sessionResult = sessionClient.getSessionById(sessionId);
                if (sessionResult == null || !sessionResult.isSuccess() || sessionResult.getData() == null) {
                    log.error("获取场次信息失败: requestId={}, sessionId={}", requestId, sessionId);
                    releaseSeats(sessionId, userId, lockId, seatIds, LockStatus.RELEASED);
                    return LockSeatResponse.builder()
                            .success(false)
                            .code(6)
                            .message("获取场次信息失败，请重试")
                            .build();
                }
                SessionResponse session = sessionResult.getData();

                // 6. 创建待支付订单
                CreatePendingOrderRequest orderRequest = CreatePendingOrderRequest.builder()
                        .userId(userId)
                        .sessionId(sessionId)
                        .lockId(lockId)
                        .eventId(session.getEventId())
                        .seatIds(seatIds)
                        .seatCount(seatIds.size())
                        .unitPrice(frontendUnitPrice)
                        .totalAmount(totalAmount)
                        .expireSeconds(orderExpireSeconds)
                        .build();

                Result<OrderResponse> orderResult = orderClient.createPendingOrder(requestId, orderRequest);
                if (orderResult != null && orderResult.isSuccess() && orderResult.getData() != null) {
                    orderNo = orderResult.getData().getOrderNo();
                    // 更新 seat_locks 的 orderNo
                    for (String seatId : seatIds) {
                        seatLockMapper.updateOrderNo(sessionId, userId, seatId, lockId, orderNo);
                    }
                    log.info("创建待支付订单成功: requestId={}, orderNo={}, userId={}, sessionId={}, amount={}",
                            requestId, orderNo, userId, sessionId, totalAmount);

                    // 调用支付系统创建支付订单，获取支付 URL
                    try {
                        PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
                                .orderNo(orderNo)
                                .amount(totalAmount)
                                .payMethod("MOCK")
                                .body("演唱会门票")
                                .build();
                        Result<PaymentCreateResponse> paymentResult = paymentClient.createPayment(paymentRequest);
                        if (paymentResult != null && paymentResult.isSuccess() && paymentResult.getData() != null) {
                            payUrl = paymentResult.getData().getPayUrl();
                            log.info("创建支付订单成功: requestId={}, orderNo={}, payUrl={}",
                                    requestId, orderNo, payUrl);
                        } else {
                            log.warn("创建支付订单失败: requestId={}, orderNo={}", requestId, orderNo);
                        }
                    } catch (Exception e) {
                        log.error("调用支付系统异常: requestId={}, orderNo={}", requestId, orderNo, e);
                    }

                } else {
                    log.error("创建待支付订单失败: requestId={}, sessionId={}, userId={}",
                            requestId, sessionId, userId);
                    releaseSeats(sessionId, userId, lockId, seatIds, LockStatus.RELEASED);
                    return LockSeatResponse.builder()
                            .success(false)
                            .code(7)
                            .message("创建订单失败，请重试")
                            .build();
                }

            } catch (Exception e) {
                log.error("系统异常: requestId={}, sessionId={}, userId={}", requestId, sessionId, userId, e);
                releaseSeats(sessionId, userId, lockId, seatIds, LockStatus.RELEASED);
                return LockSeatResponse.builder()
                        .success(false)
                        .code(8)
                        .message("系统异常，请重试")
                        .build();
            }

            return LockSeatResponse.builder()
                    .success(true)
                    .code(0)
                    .message("锁座成功")
                    .lockedSeats(seatIds)
                    .lockId(lockId)
                    .orderNo(orderNo)
                    .payUrl(payUrl)
                    .build();
        }

        String message = switch (code) {
            case 1 -> "座位不存在";
            case 2 -> "座位已被锁定或售出";
            case 3 -> "您已锁定或购买了该座位";
            default -> "系统错误";
        };

        log.warn("锁座失败: requestId={}, code={}, message={}", requestId, code, message);
        return LockSeatResponse.builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }

    /**
     * 内部方法：释放座位（不对外暴露）
     */
    @Transactional
    public void releaseSeats(Long sessionId, Long userId, String lockId, List<String> seatIds, LockStatus releaseStatus) {
        redisService.unlockSeats(sessionId, userId, lockId, seatIds);
        for (String seatId : seatIds) {
            seatLockMapper.updateStatusByLock(sessionId, userId, seatId, lockId,
                    LockStatus.LOCKED.getCode(), releaseStatus.getCode());
        }
        log.info("释放座位成功: sessionId={}, userId={}, lockId={}, status={}, count={}",
                sessionId, userId, lockId, releaseStatus, seatIds.size());
    }

    /**
     * 内部方法：标记座位已支付（供订单服务调用）
     */
    @Transactional
    public Integer markSeatLocksPaid(String orderNo, Long sessionId, Long userId, String lockId, List<String> seatIds) {
        int count = 0;
        for (String seatId : seatIds) {
            int updated = seatLockMapper.markAsPaid(sessionId, userId, seatId, lockId, orderNo);
            count += updated;
        }
        log.info("标记座位锁定已支付: orderNo={}, count={}", orderNo, count);
        return count;
    }

    @Override
    public SessionLayoutResponse getLayout(Long sessionId) {
        log.info("获取场次座位布局: sessionId={}", sessionId);

        // 1. 从Redis读取布局缓存
        Map<Object, Object> layoutData = redisService.getSessionLayout(sessionId);

        if (layoutData == null || layoutData.isEmpty()) {
            log.warn("场次布局缓存不存在: sessionId={}", sessionId);
            throw new BusinessException(404, "场次不存在或未初始化");
        }

        try {
            // 2. 解析meta数据
            String metaJson = (String) layoutData.get("meta");
            if (metaJson == null) {
                throw new BusinessException(500, "布局数据格式错误：缺少meta");
            }

            LayoutMetaDTO meta = objectMapper.readValue(metaJson, LayoutMetaDTO.class);

            // 3. 解析各区域座位数据
            List<List<SeatLayoutDTO>> areas = new ArrayList<>();
            Integer totalAreas = meta.getTotalAreas();

            for (int i = 0; i < totalAreas; i++) {
                String areaKey = "area:" + i;
                String areaJson = (String) layoutData.get(areaKey);

                if (areaJson != null) {
                    List<SeatLayoutDTO> seats = objectMapper.readValue(areaJson, new TypeReference<List<SeatLayoutDTO>>() {});
                    refreshSeatStatuses(sessionId, seats);
                    areas.add(seats);
                } else {
                    // 如果某个区域没有数据，添加空列表
                    areas.add(new ArrayList<>());
                }
            }

            // 4. 构建响应
            return SessionLayoutResponse.builder()
                    .sessionId(sessionId)
                    .meta(meta)
                    .areas(areas)
                    .build();

        } catch (Exception e) {
            log.error("解析场次布局数据失败: sessionId={}", sessionId, e);
            throw new BusinessException(500, "解析布局数据失败: " + e.getMessage());
        }
    }

    private void refreshSeatStatuses(Long sessionId, List<SeatLayoutDTO> seats) {
        if (seats == null || seats.isEmpty()) {
            return;
        }

        List<String> seatIds = seats.stream()
                .map(seat -> String.valueOf(seat.getId()))
                .toList();
        Map<String, Integer> statusMap = redisService.getEffectiveSeatStatuses(sessionId, seatIds);

        for (SeatLayoutDTO seat : seats) {
            Integer status = statusMap.get(String.valueOf(seat.getId()));
            if (status != null) {
                seat.setStatus(status);
            }
        }
    }

    @Override
    public SessionInitResponse initSession(SessionInitRequest request) {
        long startTime = System.currentTimeMillis();
        Long sessionId = request.getSessionId();

        log.info("开始初始化场次缓存: sessionId={}, totalSeats={}", sessionId, request.getSeats().size());

        try {
            // 1. 数据校验
            validateRequest(request);

            // 2. 按区域分组座位
            Map<Integer, List<SeatLayoutDTO>> seatsByArea = groupSeatsByArea(request.getSeats());

            // 3. 构建meta数据
            LayoutMetaDTO meta = LayoutMetaDTO.builder()
                    .totalSeats(request.getSeats().size())
                    .totalAreas(request.getAreaNames().size())
                    .areaNames(request.getAreaNames())
                    .areaPrices(request.getAreaPrices())
                    .build();

            // 4. 序列化JSON
            String metaJson = objectMapper.writeValueAsString(meta);
            Map<String, String> areaJsonMap = new HashMap<>();
            for (Map.Entry<Integer, List<SeatLayoutDTO>> entry : seatsByArea.entrySet()) {
                String areaKey = "area:" + entry.getKey();
                String json = objectMapper.writeValueAsString(entry.getValue());
                areaJsonMap.put(areaKey, json);
            }

            // 5. 清理旧数据
            redisService.clearSessionCache(sessionId);

            // 6. 批量写入座位状态和价格
            List<String> seatIds = request.getSeats().stream()
                    .map(s -> String.valueOf(s.getId()))
                    .collect(Collectors.toList());
            List<Integer> areaPrices = request.getSeats().stream()
                    .map(s -> request.getAreaPrices().get(s.getAreaIndex()))
                    .collect(Collectors.toList());
            redisService.initSessionData(sessionId, seatIds, areaPrices);

            // 7. 写入座位布局缓存
            redisService.saveSessionLayout(sessionId, metaJson, areaJsonMap);

            long executedTime = System.currentTimeMillis() - startTime;

            log.info("初始化场次缓存成功: sessionId={}, totalSeats={}, executedTime={}ms",
                    sessionId, request.getSeats().size(), executedTime);

            return SessionInitResponse.builder()
                    .sessionId(sessionId)
                    .success(true)
                    .message("初始化成功")
                    .totalSeats(request.getSeats().size())
                    .totalAreas(request.getAreaNames().size())
                    .executedTime(executedTime)
                    .build();

        } catch (Exception e) {
            log.error("初始化场次缓存失败: sessionId={}", sessionId, e);
            // 回滚：清理已写入的数据
            try {
                redisService.clearSessionCache(sessionId);
            } catch (Exception ex) {
                log.error("清理缓存失败: sessionId={}", sessionId, ex);
            }
            throw new BusinessException(500, "初始化失败: " + e.getMessage());
        }
    }

    /**
     * 数据校验
     */
    private void validateRequest(SessionInitRequest request) {
        // 校验区域数量匹配
        if (request.getAreaNames().size() != request.getAreaPrices().size()) {
            throw new BusinessException(400, "区域名称数量与区域价格数量不匹配");
        }

        // 校验座位ID唯一性
        Set<Long> seatIds = request.getSeats().stream()
                .map(SeatInitItem::getId)
                .collect(Collectors.toSet());
        if (seatIds.size() != request.getSeats().size()) {
            throw new BusinessException(400, "座位ID存在重复");
        }

        // 校验areaIndex范围
        int maxAreaIndex = request.getAreaNames().size() - 1;
        for (SeatInitItem seat : request.getSeats()) {
            if (seat.getAreaIndex() < 0 || seat.getAreaIndex() > maxAreaIndex) {
                throw new BusinessException(400, "座位areaIndex超出范围: " + seat.getAreaIndex());
            }
        }
    }

    /**
     * 按区域分组座位
     */
    private Map<Integer, List<SeatLayoutDTO>> groupSeatsByArea(List<SeatInitItem> seats) {
        Map<Integer, List<SeatLayoutDTO>> seatsByArea = new HashMap<>();

        for (SeatInitItem item : seats) {
            SeatLayoutDTO dto = SeatLayoutDTO.builder()
                    .id(item.getId())
                    .row(item.getRow())
                    .col(item.getCol())
                    .status(0)  // 初始状态全部为可选
                    .build();

            seatsByArea.computeIfAbsent(item.getAreaIndex(), k -> new ArrayList<>()).add(dto);
        }

        return seatsByArea;
    }

}

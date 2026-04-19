package com.duanyan.taopiaopiao.seckillservice.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.exception.BusinessException;
import com.duanyan.taopiaopiao.common.mq.message.LockAcceptedMessage;
import com.duanyan.taopiaopiao.common.redis.model.OrderProcessingCacheData;
import com.duanyan.taopiaopiao.common.redis.model.RedisLockOrderData;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.seckillservice.api.dto.InternalLockOrderResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LayoutMetaDTO;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SeatInitItem;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SeatLayoutDTO;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionLayoutResponse;
import com.duanyan.taopiaopiao.seckillservice.application.config.LockOrderNoGenerator;
import com.duanyan.taopiaopiao.seckillservice.application.mapper.LockOrderMapper;
import com.duanyan.taopiaopiao.seckillservice.application.mapper.SeatLockMapper;
import com.duanyan.taopiaopiao.seckillservice.application.producer.LockAcceptedProducer;
import com.duanyan.taopiaopiao.seckillservice.application.service.SeckillService;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.LockOrder;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.SeatLock;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockOrderStatus;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final long INITIAL_NEXT_POLL_MS = 1200L;

    private static final int DEFAULT_ORDER_EXPIRE_SECONDS = 300;
    private static final int LOCK_TTL_GRACE_SECONDS = 30;
    private static final long LOCK_ORDER_AGGREGATE_MIN_TTL_SECONDS = 7200L;

    private final RedisService redisService;
    private final SeatLockMapper seatLockMapper;
    private final LockOrderMapper lockOrderMapper;
    private final LockOrderNoGenerator lockOrderNoGenerator;
    private final ObjectMapper objectMapper;
    private final LockAcceptedProducer lockAcceptedProducer;

    @Override
    public LockSeatResponse lockSeats(LockSeatRequest request, Long userId, String requestId) {
        Long sessionId = request.getSessionId();
        List<String> seatIds = request.getSeatIds();
        BigDecimal frontendUnitPrice = request.getUnitPrice();
        int orderExpireSeconds = request.getExpireSeconds() != null
                ? request.getExpireSeconds()
                : DEFAULT_ORDER_EXPIRE_SECONDS;
        int lockExpireSeconds = orderExpireSeconds + LOCK_TTL_GRACE_SECONDS;
        LocalDateTime orderExpireTime = LocalDateTime.now().plusSeconds(orderExpireSeconds);

        String lockId = UUID.randomUUID().toString().replace("-", "");
        String orderNo = String.valueOf(lockOrderNoGenerator.nextId());
        long expireTime = System.currentTimeMillis() + lockExpireSeconds * 1000L;

        log.info("开始锁座: requestId={}, sessionId={}, userId={}, seatIds={}",
                requestId, sessionId, userId, seatIds);

        Long eventId = resolveEventId(sessionId, request.getEventId(), requestId);
        List<BigDecimal> prices = redisService.getSeatsPrice(sessionId, seatIds);
        BigDecimal totalAmount = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime createdAt = LocalDateTime.now();
        long createdAtMillis = toEpochMillis(createdAt);

        int code;
        try {
            String payloadJson = objectMapper.writeValueAsString(createLockAcceptedMessage(lockId, orderNo, requestId, userId, sessionId,
                    eventId, seatIds, frontendUnitPrice, totalAmount, orderExpireTime));
            code = redisService.lockSeatsAndRecordOrder(
                    sessionId,
                    eventId,
                    userId,
                    lockId,
                    orderNo,
                    seatIds,
                    frontendUnitPrice,
                    totalAmount,
                    lockExpireSeconds,
                    lockExpireSeconds,
                    resolveLockOrderAggregateTtlSeconds(orderExpireSeconds),
                    toEpochMillis(orderExpireTime),
                    createdAtMillis,
                    requestId,
                    payloadJson
            );
        } catch (Exception e) {
            log.error("锁座受理写入 Redis 聚合失败: requestId={}, sessionId={}, userId={}", requestId, sessionId, userId, e);
            return LockSeatResponse.builder()
                    .success(false)
                    .code(8)
                    .message("系统异常，请重试")
                    .build();
        }

        if (code == 0) {
            try {
                saveProcessingCache(orderNo, userId, sessionId, eventId, seatIds, frontendUnitPrice,
                        totalAmount, orderExpireTime, createdAt);
            } catch (Exception e) {
                log.warn("写 processing 缓存失败，不影响锁座受理: requestId={}, sessionId={}, orderNo={}",
                        requestId, sessionId, orderNo, e);
            }

            log.info("锁座受理成功: requestId={}, sessionId={}, userId={}, lockId={}, orderNo={}",
                    requestId, sessionId, userId, lockId, orderNo);
            return LockSeatResponse.builder()
                    .success(true)
                    .code(0)
                    .message("锁座成功")
                    .lockedSeats(seatIds)
                    .lockId(lockId)
                    .orderNo(orderNo)
                    .expireTime(orderExpireTime)
                    .orderStatus("PROCESSING")
                    .paymentStatus("NOT_READY")
                    .nextPollMs(INITIAL_NEXT_POLL_MS)
                    .nextAction("POLL_ORDER")
                    .build();
        }

        String message = switch (code) {
            case 1 -> "座位不存在";
            case 2 -> "座位已被锁定或售出";
            case 3 -> "您已锁定或购买了该座位";
            case 4 -> "场次与演出信息不匹配";
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
    public void releaseSeats(Long sessionId, Long userId, String lockId, List<String> seatIds, LockStatus releaseStatus) {
        redisService.unlockSeats(sessionId, userId, lockId, seatIds);
        for (String seatId : seatIds) {
            seatLockMapper.updateStatusByLock(sessionId, userId, seatId, lockId,
                    LockStatus.LOCKED.getCode(), releaseStatus.getCode());
        }
        log.info("释放座位成功: sessionId={}, userId={}, lockId={}, status={}, count={}",
                sessionId, userId, lockId, releaseStatus, seatIds.size());
    }

    public InternalLockOrderResponse getLockOrder(String orderNo, Long userId) {
        RedisLockOrderData redisLockOrder = redisService.getLockOrder(orderNo);
        if (redisLockOrder != null && userId.equals(redisLockOrder.getUserId())) {
            return buildInternalLockOrderResponse(redisLockOrder);
        }

        LockOrder lockOrder = lockOrderMapper.selectOne(
                new LambdaQueryWrapper<LockOrder>()
                        .eq(LockOrder::getOrderNo, orderNo)
                        .eq(LockOrder::getUserId, userId)
        );
        if (lockOrder == null) {
            return null;
        }

        LockOrderStatus status = resolveLockOrderStatus(lockOrder.getStatus());
        return InternalLockOrderResponse.builder()
                .lockId(lockOrder.getLockId())
                .orderNo(lockOrder.getOrderNo())
                .requestId(lockOrder.getRequestId())
                .userId(lockOrder.getUserId())
                .sessionId(lockOrder.getSessionId())
                .eventId(lockOrder.getEventId())
                .seatIds(resolveSeatIds(lockOrder.getSeatIds()))
                .seatCount(lockOrder.getSeatCount())
                .unitPrice(lockOrder.getUnitPrice())
                .totalAmount(lockOrder.getTotalAmount())
                .status(lockOrder.getStatus())
                .statusDesc(status == null ? null : status.getDesc())
                .failReason(lockOrder.getFailReason())
                .expireTime(lockOrder.getExpireTime())
                .createdAt(lockOrder.getCreatedAt())
                .build();
    }

    public boolean markLockOrderOrderCreated(String orderNo) {
        RedisLockOrderData redisLockOrder = redisService.getLockOrder(orderNo);
        if (redisLockOrder != null) {
            Integer status = redisLockOrder.getStatus();
            if (LockOrderStatus.ORDER_CREATED.getCode().equals(status)
                    || LockOrderStatus.PAID.getCode().equals(status)) {
                redisService.deleteOrderProcessing(orderNo);
                return true;
            }
            boolean redisUpdated = redisService.transitionLockOrderStatus(
                    orderNo,
                    List.of(LockOrderStatus.LOCKED.getCode(), LockOrderStatus.ORDER_CREATING.getCode()),
                    LockOrderStatus.ORDER_CREATED.getCode(),
                    "NOT_READY",
                    null,
                    false,
                    LOCK_ORDER_AGGREGATE_MIN_TTL_SECONDS
            );
            if (redisUpdated) {
                redisService.deleteOrderProcessing(orderNo);
            }
            if (redisUpdated && findLockOrder(orderNo) == null) {
                return true;
            }
        }

        LockOrder lockOrder = findLockOrder(orderNo);
        if (lockOrder == null) {
            return false;
        }
        if (LockOrderStatus.ORDER_CREATED.getCode().equals(lockOrder.getStatus())
                || LockOrderStatus.PAID.getCode().equals(lockOrder.getStatus())) {
            redisService.deleteOrderProcessing(orderNo);
            return true;
        }
        boolean updated = lockOrderMapper.markOrderCreated(
                orderNo,
                List.of(LockOrderStatus.LOCKED.getCode(), LockOrderStatus.ORDER_CREATING.getCode()),
                LockOrderStatus.ORDER_CREATED.getCode()
        ) > 0;
        if (updated) {
            redisService.deleteOrderProcessing(orderNo);
        }
        return updated;
    }

    public void requeueLockOrder(LockOrder lockOrder, String reason) {
        int updated = lockOrderMapper.updateStatus(
                lockOrder.getOrderNo(),
                List.of(LockOrderStatus.LOCKED.getCode(), LockOrderStatus.ORDER_CREATING.getCode()),
                LockOrderStatus.ORDER_CREATING.getCode(),
                reason
        );
        if (updated == 0) {
            LockOrder latest = findLockOrder(lockOrder.getOrderNo());
            if (latest == null || LockOrderStatus.ORDER_CREATED.getCode().equals(latest.getStatus())
                    || LockOrderStatus.PAID.getCode().equals(latest.getStatus())) {
                return;
            }
        }

        lockAcceptedProducer.send(createLockAcceptedMessage(
                lockOrder.getLockId(),
                lockOrder.getOrderNo(),
                lockOrder.getRequestId(),
                lockOrder.getUserId(),
                lockOrder.getSessionId(),
                lockOrder.getEventId(),
                resolveSeatIds(lockOrder.getSeatIds()),
                lockOrder.getUnitPrice(),
                lockOrder.getTotalAmount(),
                lockOrder.getExpireTime()
        ));
        saveProcessingCache(lockOrder.getOrderNo(), lockOrder.getUserId(), lockOrder.getSessionId(),
                lockOrder.getEventId(), resolveSeatIds(lockOrder.getSeatIds()), lockOrder.getUnitPrice(),
                lockOrder.getTotalAmount(), lockOrder.getExpireTime(), lockOrder.getCreatedAt());
    }

    public void markLockOrderPaid(String orderNo) {
        redisService.transitionLockOrderStatus(
                orderNo,
                List.of(
                        LockOrderStatus.LOCKED.getCode(),
                        LockOrderStatus.ORDER_CREATING.getCode(),
                        LockOrderStatus.ORDER_CREATED.getCode()
                ),
                LockOrderStatus.PAID.getCode(),
                "SUCCESS",
                null,
                true,
                LOCK_ORDER_AGGREGATE_MIN_TTL_SECONDS
        );

        int updated = lockOrderMapper.updateStatus(
                orderNo,
                List.of(
                        LockOrderStatus.LOCKED.getCode(),
                        LockOrderStatus.ORDER_CREATING.getCode(),
                        LockOrderStatus.ORDER_CREATED.getCode()
                ),
                LockOrderStatus.PAID.getCode(),
                null
        );
        if (updated == 0) {
            LockOrder latest = findLockOrder(orderNo);
            if (latest == null || !LockOrderStatus.PAID.getCode().equals(latest.getStatus())) {
                log.warn("锁单未更新为已支付: orderNo={}, currentStatus={}",
                        orderNo, latest == null ? null : latest.getStatus());
            }
        }
        redisService.deleteOrderProcessing(orderNo);
    }

    public void markLockOrderReleased(String orderNo, LockOrderStatus targetStatus, String failReason) {
        redisService.transitionLockOrderStatus(
                orderNo,
                List.of(
                        LockOrderStatus.LOCKED.getCode(),
                        LockOrderStatus.ORDER_CREATING.getCode(),
                        LockOrderStatus.ORDER_CREATED.getCode()
                ),
                targetStatus.getCode(),
                "NOT_AVAILABLE",
                failReason,
                true,
                LOCK_ORDER_AGGREGATE_MIN_TTL_SECONDS
        );

        int updated = lockOrderMapper.updateStatus(
                orderNo,
                List.of(
                        LockOrderStatus.LOCKED.getCode(),
                        LockOrderStatus.ORDER_CREATING.getCode(),
                        LockOrderStatus.ORDER_CREATED.getCode()
                ),
                targetStatus.getCode(),
                failReason
        );
        if (updated == 0) {
            LockOrder latest = findLockOrder(orderNo);
            if (latest == null || !targetStatus.getCode().equals(latest.getStatus())) {
                log.warn("锁单未更新为目标状态: orderNo={}, targetStatus={}, currentStatus={}",
                        orderNo, targetStatus, latest == null ? null : latest.getStatus());
            }
        }
        redisService.deleteOrderProcessing(orderNo);
    }

    public void timeoutLockOrder(LockOrder lockOrder, String failReason) {
        releaseSeats(
                lockOrder.getSessionId(),
                lockOrder.getUserId(),
                lockOrder.getLockId(),
                resolveSeatIds(lockOrder.getSeatIds()),
                LockStatus.EXPIRED
        );
        markLockOrderReleased(lockOrder.getOrderNo(), LockOrderStatus.TIMEOUT, failReason);
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
            redisService.saveSessionMeta(sessionId, request.getEventId());

            long executedTime = System.currentTimeMillis() - startTime;

            log.info("初始化场次缓存成功: sessionId={}, totalSeats={}, executedTime={}ms",
                    sessionId, request.getSeats().size(), executedTime);

            return SessionInitResponse.builder()
                    .sessionId(sessionId)
                    .eventId(request.getEventId())
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

    private Long resolveEventId(Long sessionId, Long requestEventId, String requestId) {
        Map<String, Object> sessionMeta = redisService.getSessionMeta(sessionId);
        if (sessionMeta == null || sessionMeta.get("eventId") == null) {
            log.error("场次快照不存在: requestId={}, sessionId={}", requestId, sessionId);
            throw new BusinessException(400, "场次快照不存在，请先初始化场次缓存");
        }

        Long cachedEventId = Long.valueOf(String.valueOf(sessionMeta.get("eventId")));
        if (requestEventId == null) {
            log.warn("锁座请求缺少 eventId，按快照兜底: requestId={}, sessionId={}, eventId={}",
                    requestId, sessionId, cachedEventId);
            return cachedEventId;
        }

        if (!cachedEventId.equals(requestEventId)) {
            log.warn("锁座请求 eventId 与场次快照不匹配: requestId={}, sessionId={}, requestEventId={}, cachedEventId={}",
                    requestId, sessionId, requestEventId, cachedEventId);
            throw new BusinessException(400, "场次与演出信息不匹配");
        }

        return cachedEventId;
    }

    private LockAcceptedMessage createLockAcceptedMessage(String lockId,
                                                         String orderNo,
                                                         String requestId,
                                                         Long userId,
                                                         Long sessionId,
                                                         Long eventId,
                                                         List<String> seatIds,
                                                         BigDecimal unitPrice,
                                                         BigDecimal totalAmount,
                                                         LocalDateTime expireTime) {
        return LockAcceptedMessage.builder()
                .lockId(lockId)
                .orderNo(orderNo)
                .requestId(requestId)
                .userId(userId)
                .sessionId(sessionId)
                .eventId(eventId)
                .seatIds(seatIds)
                .seatCount(seatIds.size())
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .expireTime(expireTime)
                .build();
    }

    private void saveProcessingCache(String orderNo,
                                     Long userId,
                                     Long sessionId,
                                     Long eventId,
                                     List<String> seatIds,
                                     BigDecimal unitPrice,
                                     BigDecimal totalAmount,
                                     LocalDateTime expireTime,
                                     LocalDateTime createdAt) {
        redisService.saveOrderProcessing(OrderProcessingCacheData.builder()
                .orderNo(orderNo)
                .userId(userId)
                .sessionId(sessionId)
                .eventId(eventId)
                .seatIds(seatIds)
                .seatCount(seatIds.size())
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .status("PROCESSING")
                .paymentStatus("NOT_READY")
                .expireTime(expireTime)
                .createdAt(createdAt)
                .build(), resolveProcessingCacheTtl(expireTime));
    }

    private long resolveProcessingCacheTtl(LocalDateTime expireTime) {
        if (expireTime == null) {
            return 60L;
        }
        return Math.max(30L, java.time.Duration.between(LocalDateTime.now(), expireTime).getSeconds() + 30L);
    }

    private LockOrder findLockOrder(String orderNo) {
        return lockOrderMapper.selectOne(
                new LambdaQueryWrapper<LockOrder>()
                        .eq(LockOrder::getOrderNo, orderNo)
        );
    }

    private LockOrderStatus resolveLockOrderStatus(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }
        for (LockOrderStatus status : LockOrderStatus.values()) {
            if (status.getCode().equals(statusCode)) {
                return status;
            }
        }
        return null;
    }

    private List<String> resolveSeatIds(List<String> seatIds) {
        return seatIds == null ? List.of() : seatIds;
    }

    private InternalLockOrderResponse buildInternalLockOrderResponse(RedisLockOrderData redisLockOrder) {
        LockOrderStatus status = resolveLockOrderStatus(redisLockOrder.getStatus());
        return InternalLockOrderResponse.builder()
                .lockId(redisLockOrder.getLockId())
                .orderNo(redisLockOrder.getOrderNo())
                .requestId(redisLockOrder.getRequestId())
                .userId(redisLockOrder.getUserId())
                .sessionId(redisLockOrder.getSessionId())
                .eventId(redisLockOrder.getEventId())
                .seatIds(redisLockOrder.getSeatIds())
                .seatCount(redisLockOrder.getSeatCount())
                .unitPrice(redisLockOrder.getUnitPrice())
                .totalAmount(redisLockOrder.getTotalAmount())
                .status(redisLockOrder.getStatus())
                .statusDesc(status == null ? null : status.getDesc())
                .failReason(redisLockOrder.getFailReason())
                .expireTime(redisLockOrder.getExpireTime())
                .createdAt(redisLockOrder.getCreatedAt())
                .build();
    }

    private long resolveLockOrderAggregateTtlSeconds(int orderExpireSeconds) {
        return Math.max(LOCK_ORDER_AGGREGATE_MIN_TTL_SECONDS, orderExpireSeconds + 3600L);
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
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

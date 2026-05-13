package com.duanyan.taopiaopiao.seckillservice.application.service.impl;

import com.duanyan.taopiaopiao.common.exception.BusinessException;
import com.duanyan.taopiaopiao.common.dto.PrepareOrderRequest;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LayoutMetaDTO;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SeatInitItem;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SeatLayoutDTO;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionLayoutResponse;
import com.duanyan.taopiaopiao.seckillservice.application.client.OrderInternalClient;
import com.duanyan.taopiaopiao.seckillservice.application.config.LockOrderNoGenerator;
import com.duanyan.taopiaopiao.seckillservice.application.service.SeckillService;
import com.duanyan.taopiaopiao.seckillservice.application.tcc.SeatTccAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeckillServiceImpl implements SeckillService {

    private static final long INITIAL_NEXT_POLL_MS = 1200L;

    private static final int DEFAULT_ORDER_EXPIRE_SECONDS = 300;
    private static final int LOCK_TTL_GRACE_SECONDS = 30;

    private final RedisService redisService;
    private final OrderInternalClient orderInternalClient;
    private final SeatTccAction seatTccAction;
    private final LockOrderNoGenerator lockOrderNoGenerator;
    private final ObjectMapper objectMapper;

    @Override
    @GlobalTransactional(name = "lock-order-tcc", rollbackFor = Exception.class, timeoutMills = 5000)
    public LockSeatResponse lockSeats(LockSeatRequest request, Long userId, String requestId) {
        Long sessionId = request.getSessionId();
        List<String> seatIds = request.getSeatIds();
        BigDecimal frontendUnitPrice = request.getUnitPrice();
        int orderExpireSeconds = request.getExpireSeconds() != null
                ? request.getExpireSeconds()
                : DEFAULT_ORDER_EXPIRE_SECONDS;
        int lockExpireSeconds = orderExpireSeconds + LOCK_TTL_GRACE_SECONDS;
        LocalDateTime orderExpireTime = LocalDateTime.now().plusSeconds(orderExpireSeconds);

        String orderNo = String.valueOf(lockOrderNoGenerator.nextId());

        log.info("开始锁座: requestId={}, sessionId={}, userId={}, seatIds={}",
                requestId, sessionId, userId, seatIds);

        Long eventId = resolveEventId(sessionId, request.getEventId(), requestId);
        List<BigDecimal> prices = redisService.getSeatsPrice(sessionId, seatIds);
        BigDecimal totalAmount = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        seatTccAction.tryReserve(
                null,
                orderNo,
                userId,
                sessionId,
                eventId,
                seatIds,
                lockExpireSeconds
        );

        Result<Boolean> result = orderInternalClient.prepare(
                PrepareOrderRequest.builder()
                        .orderNo(orderNo)
                        .requestId(requestId)
                        .userId(userId)
                        .sessionId(sessionId)
                        .eventId(eventId)
                        .seatIds(seatIds)
                        .seatCount(seatIds.size())
                        .unitPrice(frontendUnitPrice)
                        .totalAmount(totalAmount)
                        .expireTime(orderExpireTime)
                        .build()
        );
        if (result == null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
            throw new IllegalStateException("订单 TCC Try 失败");
        }

        log.info("锁座受理成功: requestId={}, sessionId={}, userId={}, orderNo={}",
                requestId, sessionId, userId, orderNo);
        return LockSeatResponse.builder()
                .success(true)
                .code(0)
                .message("锁座成功")
                .lockedSeats(seatIds)
                .orderNo(orderNo)
                .expireTime(orderExpireTime)
                .orderStatus("UNPAID")
                .paymentStatus("NOT_READY")
                .nextPollMs(INITIAL_NEXT_POLL_MS)
                .nextAction("POLL_ORDER")
                .build();
    }

    public boolean confirmOrder(String orderNo,
                                Long sessionId,
                                Long userId,
                                List<String> seatIds) {
        return redisService.markSeatsPaid(sessionId, userId, orderNo, seatIds);
    }

    public boolean cancelOrder(String orderNo,
                               Long sessionId,
                               Long userId,
                               List<String> seatIds,
                               String reason) {
        return redisService.releaseHeldSeats(sessionId, orderNo, seatIds);
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

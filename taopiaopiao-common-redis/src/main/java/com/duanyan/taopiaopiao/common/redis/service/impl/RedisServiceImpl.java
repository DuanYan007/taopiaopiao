package com.duanyan.taopiaopiao.common.redis.service.impl;

import com.duanyan.taopiaopiao.common.redis.constants.RedisKey;
import com.duanyan.taopiaopiao.common.redis.constants.SeatStatus;
import com.duanyan.taopiaopiao.common.redis.model.OrderProcessingCacheData;
import com.duanyan.taopiaopiao.common.redis.model.RedisLockOrderData;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 服务实现
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(RedissonClient.class)
public class RedisServiceImpl implements RedisService {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static String LOCK_SEAT_AND_RECORD_ORDER_SCRIPT;
    private static String UNLOCK_SEAT_SCRIPT;
    private static String CONFIRM_PURCHASE_SCRIPT;
    private static String UPDATE_LOCK_ORDER_STATUS_SCRIPT;

    static {
        try {
            LOCK_SEAT_AND_RECORD_ORDER_SCRIPT = readScript("lua/lock_seat_and_record_order.lua");
            UNLOCK_SEAT_SCRIPT = readScript("lua/unlock_seat.lua");
            CONFIRM_PURCHASE_SCRIPT = readScript("lua/confirm_purchase.lua");
            UPDATE_LOCK_ORDER_STATUS_SCRIPT = readScript("lua/update_lock_order_status.lua");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua scripts", e);
        }
    }

    private static String readScript(String path) throws IOException {
        org.springframework.core.io.Resource resource = new ClassPathResource(path);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public List<BigDecimal> getSeatsPrice(Long sessionId, List<String> seatIds) {
        List<BigDecimal> pricesList = new ArrayList<>();
        RBatch batch = redissonClient.createBatch();
        for(String seatId : seatIds){
            String seatPriceKey = RedisKey.seatPriceKey(sessionId, seatId);
            batch.getBucket(seatPriceKey).getAsync();
        }
        List<?> responses = batch.execute().getResponses();
        for(int i = 0; i < seatIds.size(); i++){
            Object value = responses.get(i);
            pricesList.add(new BigDecimal(value.toString()));
        }
        return pricesList;
    }

    @Override
    public int lockSeatsAndRecordOrder(Long sessionId,
                                       Long eventId,
                                       Long userId,
                                       String lockId,
                                       String orderNo,
                                       List<String> seatIds,
                                       BigDecimal unitPrice,
                                       BigDecimal totalAmount,
                                       int seatLockExpireSeconds,
                                       int userLockExpireSeconds,
                                       long lockOrderTtlSeconds,
                                       long expireTimeMillis,
                                       long createdAtMillis,
                                       String requestId,
                                       String payloadJson) {
        List<Object> keys = List.of(String.valueOf(sessionId));
        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(userId));
        params.add(lockId);
        params.add(orderNo);
        params.add(String.valueOf(eventId));
        params.add(String.valueOf(seatIds.size()));
        params.add(String.valueOf(seatLockExpireSeconds));
        params.add(String.valueOf(userLockExpireSeconds));
        params.add(String.valueOf(lockOrderTtlSeconds));
        params.add(unitPrice == null ? "0" : unitPrice.toPlainString());
        params.add(totalAmount == null ? "0" : totalAmount.toPlainString());
        params.add(String.valueOf(expireTimeMillis));
        params.add(String.valueOf(createdAtMillis));
        params.add(requestId == null ? "" : requestId);
        params.add(payloadJson);
        try {
            params.add(objectMapper.writeValueAsString(seatIds));
        } catch (IOException e) {
            throw new RuntimeException("序列化座位列表失败", e);
        }
        params.add("1");
        params.add("NOT_READY");
        params.addAll(seatIds);

        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                LOCK_SEAT_AND_RECORD_ORDER_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                params.toArray()
        );
        return result.intValue();
    }

    @Override
    public int unlockSeats(Long sessionId, Long userId, String lockId, List<String> seatIds) {
        List<Object> keys = List.of(String.valueOf(sessionId));

        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(userId));
        params.add(lockId);
        params.add(String.valueOf(seatIds.size()));
        params.addAll(seatIds);

        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                UNLOCK_SEAT_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                params.toArray()
        );

        int count = result.intValue();
        log.info("释放座位: sessionId={}, userId={}, lockId={}, count={}", sessionId, userId, lockId, count);

        return count;
    }

    @Override
    public boolean confirmPurchase(Long sessionId, Long userId, String lockId, List<String> seatIds) {
        List<Object> keys = List.of(String.valueOf(sessionId));

        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(userId));
        params.add(lockId);
        params.add(String.valueOf(seatIds.size()));
        params.addAll(seatIds);

        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                CONFIRM_PURCHASE_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                params.toArray()
        );

        boolean success = result.intValue() == 0;
        log.info("确认购买: sessionId={}, userId={}, lockId={}, success={}", sessionId, userId, lockId, success);

        return success;
    }

    @Override
    public java.util.Map<Object, Object> getSessionLayout(Long sessionId) {
        String layoutKey = "session:layout:" + sessionId;
        org.redisson.api.RMap<Object, Object> map = redissonClient.getMap(layoutKey);

        if (!map.isExists()) {
            return null;
        }

        return map.readAllMap();
    }

    @Override
    public void initSessionData(Long sessionId, java.util.List<String> seatIds, java.util.List<Integer> areaPrices) {
        String sessionIdStr = String.valueOf(sessionId);
        int batchSize = 1000;
        int totalSeats = seatIds.size();

        // 分批写入座位状态和价格
        for (int i = 0; i < totalSeats; i += batchSize) {
            int end = Math.min(i + batchSize, totalSeats);
            RBatch batch = redissonClient.createBatch();

            for (int j = i; j < end; j++) {
                String seatId = seatIds.get(j);
                Integer price = areaPrices.get(j);

                // 写入座位状态
                String seatKey = RedisKey.seatStateKey(sessionId, seatId);
                batch.getBucket(seatKey).setAsync(0);

                // 写入座位价格
                String priceKey = RedisKey.PRICE_PREFIX + sessionIdStr + ":" + seatId;
                batch.getBucket(priceKey).setAsync(price);
            }

            batch.execute();
            log.info("初始化场次座位数据: sessionId={}, 进度={}/{}", sessionId, end, totalSeats);
        }

        log.info("初始化场次数据完成: sessionId={}, totalSeats={}", sessionId, totalSeats);
    }

    @Override
    public void clearSessionCache(Long sessionId) {
        String sessionIdStr = String.valueOf(sessionId);

        // 删除座位布局缓存
        String layoutKey = "session:layout:" + sessionIdStr;
        redissonClient.getBucket(layoutKey).delete();

        String sessionMetaKey = RedisKey.sessionMetaKey(sessionId);
        redissonClient.getBucket(sessionMetaKey).delete();

        // 删除所有座位状态和价格（使用scan）
        // 兼容迁移：顺手清理旧版 seat:{sessionId}:{seatId} 结构，避免初始化后仍有遗留脏数据。
        String legacySeatPattern = "seat:" + sessionIdStr + ":*";
        String seatStatePattern = RedisKey.SEAT_STATE_PREFIX + sessionIdStr + ":*";
        String seatLockPattern = RedisKey.SEAT_LOCK_PREFIX + sessionIdStr + ":*";
        String pricePattern = RedisKey.PRICE_PREFIX + sessionIdStr + ":*";
        String lockUserPattern = RedisKey.LOCK_USER_PREFIX + sessionIdStr + ":*";

        Iterable<String> legacySeatKeys = redissonClient.getKeys().getKeysByPattern(legacySeatPattern);
        for (String key : legacySeatKeys) {
            redissonClient.getBucket(key).delete();
        }

        Iterable<String> seatStateKeys = redissonClient.getKeys().getKeysByPattern(seatStatePattern);
        for (String key : seatStateKeys) {
            redissonClient.getBucket(key).delete();
        }

        Iterable<String> seatLockKeys = redissonClient.getKeys().getKeysByPattern(seatLockPattern);
        for (String key : seatLockKeys) {
            redissonClient.getBucket(key).delete();
        }

        Iterable<String> priceKeys = redissonClient.getKeys().getKeysByPattern(pricePattern);
        for (String key : priceKeys) {
            redissonClient.getBucket(key).delete();
        }

        Iterable<String> lockUserKeys = redissonClient.getKeys().getKeysByPattern(lockUserPattern);
        for (String key : lockUserKeys) {
            redissonClient.getBucket(key).delete();
        }

        redissonClient.getScoredSortedSet(RedisKey.lockExpireKey(sessionId)).delete();
        redissonClient.getStream(RedisKey.lockAcceptedStreamKey(sessionId), StringCodec.INSTANCE).delete();

        Iterable<String> lockOrderKeys = redissonClient.getKeys().getKeysByPattern(RedisKey.LOCK_ORDER_PREFIX + "*");
        for (String key : lockOrderKeys) {
            RMap<String, String> lockOrderMap = redissonClient.getMap(key, StringCodec.INSTANCE);
            String lockOrderSessionId = lockOrderMap.get("sessionId");
            if (sessionIdStr.equals(lockOrderSessionId)) {
                lockOrderMap.delete();
            }
        }

        log.info("清除场次缓存: sessionId={}", sessionId);
    }

    @Override
    public void saveSessionLayout(Long sessionId, String metaJson, java.util.Map<String, String> areaJsonMap) {
        String layoutKey = "session:layout:" + sessionId;
        RMap<String, String> layoutMap = redissonClient.getMap(layoutKey);

        layoutMap.put("meta", metaJson);
        for (java.util.Map.Entry<String, String> entry : areaJsonMap.entrySet()) {
            layoutMap.put(entry.getKey(), entry.getValue());
        }

        log.info("保存场次布局缓存: sessionId={}, areas={}", sessionId, areaJsonMap.size());
    }

    @Override
    public void saveSessionMeta(Long sessionId, Long eventId) {
        RMap<String, Object> sessionMeta = redissonClient.getMap(RedisKey.sessionMetaKey(sessionId));
        sessionMeta.put("sessionId", sessionId);
        sessionMeta.put("eventId", eventId);
        log.info("保存场次快照元数据: sessionId={}, eventId={}", sessionId, eventId);
    }

    @Override
    public Map<String, Object> getSessionMeta(Long sessionId) {
        RMap<String, Object> sessionMeta = redissonClient.getMap(RedisKey.sessionMetaKey(sessionId));
        if (!sessionMeta.isExists()) {
            return null;
        }
        return new HashMap<>(sessionMeta.readAllMap());
    }

    @Override
    public Map<String, Integer> getEffectiveSeatStatuses(Long sessionId, List<String> seatIds) {
        Map<String, Integer> statuses = new HashMap<>();
        if (seatIds == null || seatIds.isEmpty()) {
            return statuses;
        }

        RBatch batch = redissonClient.createBatch();
        for (String seatId : seatIds) {
            batch.getBucket(RedisKey.seatStateKey(sessionId, seatId)).getAsync();
            batch.getBucket(RedisKey.seatLockKey(sessionId, seatId)).getAsync();
        }

        List<?> responses = batch.execute().getResponses();
        for (int i = 0; i < seatIds.size(); i++) {
            String seatId = seatIds.get(i);
            Object stateValue = responses.get(i * 2);
            Object lockValue = responses.get(i * 2 + 1);

            int status = SeatStatus.AVAILABLE.getCode();
            if (stateValue != null && String.valueOf(SeatStatus.SOLD.getCode()).equals(String.valueOf(stateValue))) {
                status = SeatStatus.SOLD.getCode();
            } else if (lockValue != null) {
                status = 1;
            }

            statuses.put(seatId, status);
        }

        return statuses;
    }

    @Override
    public void saveOrderProcessing(OrderProcessingCacheData data, long ttlSeconds) {
        if (data == null || data.getOrderNo() == null) {
            return;
        }
        long effectiveTtl = Math.max(1L, ttlSeconds);
        try {
            String payload = objectMapper.writeValueAsString(data);
            redissonClient.getBucket(RedisKey.orderProcessingKey(data.getOrderNo()), StringCodec.INSTANCE)
                    .set(payload, effectiveTtl, TimeUnit.SECONDS);
        } catch (IOException e) {
            throw new RuntimeException("序列化 processing 缓存失败", e);
        }
    }

    @Override
    public OrderProcessingCacheData getOrderProcessing(String orderNo) {
        if (orderNo == null) {
            return null;
        }
        RBucket<Object> bucket = redissonClient.getBucket(RedisKey.orderProcessingKey(orderNo));
        Object raw = bucket.get();
        if (raw == null) {
            return null;
        }

        try {
            if (raw instanceof OrderProcessingCacheData cacheData) {
                return cacheData;
            }
            if (raw instanceof String rawJson) {
                return objectMapper.readValue(rawJson, OrderProcessingCacheData.class);
            }
            return objectMapper.convertValue(raw, OrderProcessingCacheData.class);
        } catch (IllegalArgumentException | IOException e) {
            log.warn("解析 processing 缓存失败，删除异常缓存: orderNo={}, rawType={}",
                    orderNo, raw.getClass().getName(), e);
            bucket.delete();
            return null;
        }
    }

    @Override
    public void deleteOrderProcessing(String orderNo) {
        if (orderNo == null) {
            return;
        }
        redissonClient.getBucket(RedisKey.orderProcessingKey(orderNo)).delete();
    }

    @Override
    public RedisLockOrderData getLockOrder(String orderNo) {
        if (orderNo == null) {
            return null;
        }
        RMap<String, String> lockOrderMap = redissonClient.getMap(RedisKey.lockOrderKey(orderNo), StringCodec.INSTANCE);
        if (!lockOrderMap.isExists()) {
            return null;
        }

        Map<String, String> raw = lockOrderMap.readAllMap();
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        try {
            return RedisLockOrderData.builder()
                    .lockId(raw.get("lockId"))
                    .orderNo(raw.get("orderNo"))
                    .requestId(raw.get("requestId"))
                    .userId(parseLong(raw.get("userId")))
                    .sessionId(parseLong(raw.get("sessionId")))
                    .eventId(parseLong(raw.get("eventId")))
                    .seatIds(objectMapper.readValue(raw.getOrDefault("seatIdsJson", "[]"), new TypeReference<List<String>>() {}))
                    .seatCount(parseInteger(raw.get("seatCount")))
                    .unitPrice(parseBigDecimal(raw.get("unitPrice")))
                    .totalAmount(parseBigDecimal(raw.get("totalAmount")))
                    .status(parseInteger(raw.get("status")))
                    .paymentStatus(raw.get("paymentStatus"))
                    .failReason(raw.get("failReason"))
                    .expireTime(parseDateTime(raw.get("expireTimeMillis")))
                    .createdAt(parseDateTime(raw.get("createdAtMillis")))
                    .updatedAt(parseDateTime(raw.get("updatedAtMillis")))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("解析 Redis 锁单失败", e);
        }
    }

    @Override
    public boolean transitionLockOrderStatus(String orderNo,
                                             List<Integer> expectedStatuses,
                                             Integer targetStatus,
                                             String paymentStatus,
                                             String failReason,
                                             boolean clearUserLockIndex,
                                             long ttlSeconds) {
        if (orderNo == null || expectedStatuses == null || expectedStatuses.isEmpty() || targetStatus == null) {
            return false;
        }

        List<Object> keys = List.of(orderNo);
        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(expectedStatuses.size()));
        expectedStatuses.stream().map(String::valueOf).forEach(params::add);
        params.add(String.valueOf(targetStatus));
        params.add(paymentStatus == null ? "" : paymentStatus);
        params.add(failReason == null ? "" : failReason);
        params.add(String.valueOf(System.currentTimeMillis()));
        params.add(clearUserLockIndex ? "1" : "0");
        params.add(String.valueOf(Math.max(1L, ttlSeconds)));

        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                UPDATE_LOCK_ORDER_STATUS_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                params.toArray()
        );
        return result != null && result == 1L;
    }

    private Long parseLong(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    private Integer parseInteger(String value) {
        return value == null ? null : Integer.valueOf(value);
    }

    private BigDecimal parseBigDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)), ZoneId.systemDefault());
    }
}

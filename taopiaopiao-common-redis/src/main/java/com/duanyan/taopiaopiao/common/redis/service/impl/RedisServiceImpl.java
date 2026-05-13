package com.duanyan.taopiaopiao.common.redis.service.impl;

import com.duanyan.taopiaopiao.common.redis.constants.RedisKey;
import com.duanyan.taopiaopiao.common.redis.constants.SeatStatus;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static String CONFIRM_RESERVE_SEAT_SCRIPT;
    private static String CANCEL_RESERVE_SEAT_SCRIPT;
    private static String MARK_PAID_SEAT_SCRIPT;
    private static String RELEASE_HELD_SEAT_SCRIPT;
    static {
        try {
            LOCK_SEAT_AND_RECORD_ORDER_SCRIPT = readScript("lua/lock_seat_and_record_order.lua");
            CONFIRM_RESERVE_SEAT_SCRIPT = readScript("lua/confirm_reserve_seat.lua");
            CANCEL_RESERVE_SEAT_SCRIPT = readScript("lua/cancel_reserve_seat.lua");
            MARK_PAID_SEAT_SCRIPT = readScript("lua/mark_paid_seat.lua");
            RELEASE_HELD_SEAT_SCRIPT = readScript("lua/release_held_seat.lua");
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
    public int tryReserveSeatsTcc(Long sessionId,
                                  Long eventId,
                                  Long userId,
                                  String orderNo,
                                  String xid,
                                  List<String> seatIds,
                                  int seatLockExpireSeconds,
                                  int userLockExpireSeconds) {
        List<Object> keys = List.of(String.valueOf(sessionId));
        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(userId));
        params.add(orderNo);
        params.add(xid);
        params.add(String.valueOf(eventId));
        params.add(String.valueOf(seatIds.size()));
        params.add(String.valueOf(seatLockExpireSeconds));
        params.add(String.valueOf(userLockExpireSeconds));
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
    public int cancelReserveSeatsTcc(Long sessionId,
                                     Long userId,
                                     String orderNo,
                                     String xid,
                                     List<String> seatIds,
                                     int cancelMarkerExpireSeconds) {
        List<Object> keys = List.of(String.valueOf(sessionId));

        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(userId));
        params.add(orderNo);
        params.add(xid);
        params.add(String.valueOf(seatIds.size()));
        params.add(String.valueOf(cancelMarkerExpireSeconds));
        params.addAll(seatIds);

        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                CANCEL_RESERVE_SEAT_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                params.toArray()
        );
        int code = result.intValue();
        log.info("TCC Cancel 座位预留: sessionId={}, userId={}, orderNo={}, code={}", sessionId, userId, orderNo, code);
        return code;
    }

    @Override
    public int confirmReserveSeatsTcc(Long sessionId,
                                      Long userId,
                                      String orderNo,
                                      String xid,
                                      List<String> seatIds) {
        List<Object> keys = List.of(String.valueOf(sessionId));

        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(userId));
        params.add(orderNo);
        params.add(xid);
        params.add(String.valueOf(seatIds.size()));
        params.addAll(seatIds);

        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                CONFIRM_RESERVE_SEAT_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                params.toArray()
        );
        int code = result.intValue();
        log.info("TCC Confirm 座位预留: sessionId={}, userId={}, orderNo={}, code={}", sessionId, userId, orderNo, code);
        return code;
    }

    @Override
    public boolean markSeatsPaid(Long sessionId, Long userId, String orderNo, List<String> seatIds) {
        List<Object> keys = List.of(String.valueOf(sessionId));
        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(userId));
        params.add(orderNo);
        params.add(String.valueOf(seatIds.size()));
        params.addAll(seatIds);

        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                MARK_PAID_SEAT_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                params.toArray()
        );
        boolean success = result.intValue() == 0;
        log.info("支付成功后确认售出: sessionId={}, userId={}, orderNo={}, success={}", sessionId, userId, orderNo, success);
        return success;
    }

    @Override
    public boolean releaseHeldSeats(Long sessionId, String orderNo, List<String> seatIds) {
        List<Object> keys = List.of(String.valueOf(sessionId));
        List<Object> params = new ArrayList<>();
        params.add(orderNo);
        params.add(String.valueOf(seatIds.size()));
        params.addAll(seatIds);

        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                RELEASE_HELD_SEAT_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                params.toArray()
        );
        boolean success = result.intValue() == 0;
        log.info("释放长期占座: sessionId={}, orderNo={}, success={}", sessionId, orderNo, success);
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
            } else if (stateValue != null && String.valueOf(SeatStatus.RESERVED.getCode()).equals(String.valueOf(stateValue))) {
                status = SeatStatus.RESERVED.getCode();
            } else if (lockValue != null) {
                status = SeatStatus.RESERVED.getCode();
            }

            statuses.put(seatId, status);
        }

        return statuses;
    }

}

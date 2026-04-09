package com.duanyan.taopiaopiao.common.redis.service.impl;

import com.duanyan.taopiaopiao.common.redis.constants.RedisKey;
import com.duanyan.taopiaopiao.common.redis.constants.SeatStatus;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
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

    private static String LOCK_SEAT_SCRIPT;
    private static String UNLOCK_SEAT_SCRIPT;
    private static String CONFIRM_PURCHASE_SCRIPT;

    static {
        try {
            LOCK_SEAT_SCRIPT = readScript("lua/lock_seat.lua");
            UNLOCK_SEAT_SCRIPT = readScript("lua/unlock_seat.lua");
            CONFIRM_PURCHASE_SCRIPT = readScript("lua/confirm_purchase.lua");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua scripts", e);
        }
    }

    private static String readScript(String path) throws IOException {
        org.springframework.core.io.Resource resource = new ClassPathResource(path);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public void initSessionSeats(Long sessionId, List<String> seatIds) {
        String sessionSeatsKey = RedisKey.sessionSeatsKey(sessionId);

        RBatch batch = redissonClient.createBatch();

        // 批量设置座位初始状态为可选(0)
        for (String seatId : seatIds) {
            String seatKey = RedisKey.seatStateKey(sessionId, seatId);
            batch.getBucket(seatKey).setAsync(SeatStatus.AVAILABLE.getCode());
        }

        // 添加到场次座位集合
        batch.getSet(sessionSeatsKey).addAllAsync(seatIds);

        batch.execute();
        log.info("初始化场次座位数据, sessionId: {}, seatCount: {}", sessionId, seatIds.size());
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
    public int lockSeats(Long sessionId, Long userId, String lockId, List<String> seatIds, int expireSeconds) {
        List<Object> keys = List.of(String.valueOf(sessionId));

        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(userId));
        params.add(lockId);
        params.add(String.valueOf(seatIds.size()));
        params.add(String.valueOf(expireSeconds));
        params.addAll(seatIds);

        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                LOCK_SEAT_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                params.toArray()
        );

        int code = result.intValue();
        log.info("锁座结果: sessionId={}, userId={}, lockId={}, seatIds={}, code={}",
                sessionId, userId, lockId, seatIds, code);

        return code;
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
    public SeatStatus getSeatStatus(Long sessionId, String seatId) {
        String seatKey = RedisKey.seatStateKey(sessionId, seatId);
        Integer code = (Integer) redissonClient.getBucket(seatKey).get();

        if (code == null) {
            return null;
        }

        return SeatStatus.fromCode(code);
    }

    @Override
    public void setSeatStatus(Long sessionId, String seatId, SeatStatus status) {
        String seatKey = RedisKey.seatStateKey(sessionId, seatId);
        redissonClient.getBucket(seatKey).set(status.getCode());
    }

    @Override
    public void clearSessionData(Long sessionId) {
        String sessionSeatsKey = RedisKey.sessionSeatsKey(sessionId);
        String soldoutKey = RedisKey.sessionSoldoutKey(sessionId);

        // 先读取座位ID列表（在删除之前）
        Iterable<Object> seatIdObjects = redissonClient.getSet(sessionSeatsKey).readAll();
        List<String> seatIds = new java.util.ArrayList<>();
        for (Object obj : seatIdObjects) {
            seatIds.add(String.valueOf(obj));
        }

        RBatch batch = redissonClient.createBatch();

        // 删除所有座位状态
        for (String seatId : seatIds) {
            batch.getBucket(RedisKey.seatStateKey(sessionId, seatId)).deleteAsync();
            batch.getBucket(RedisKey.seatLockKey(sessionId, seatId)).deleteAsync();
        }

        // 删除场次座位集合
        batch.getSet(sessionSeatsKey).deleteAsync();

        // 删除售罄标志
        batch.getBucket(soldoutKey).deleteAsync();

        batch.execute();
        log.info("清除场次数据: sessionId={}, seatCount={}", sessionId, seatIds.size());
    }

    @Override
    public long getUserLockedSeatCount(Long userId) {
        long count = 0;
        Iterable<String> lockKeys = redissonClient.getKeys().getKeysByPattern(RedisKey.SEAT_LOCK_PREFIX + "*");
        String prefix = userId + "|";
        for (String key : lockKeys) {
            Object value = redissonClient.getBucket(key).get();
            if (value != null && value.toString().startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void removeUserLocks(Long userId, List<String> seatIds) {
        String prefix = userId + "|";
        for (String seatId : seatIds) {
            Iterable<String> lockKeys = redissonClient.getKeys().getKeysByPattern(RedisKey.SEAT_LOCK_PREFIX + "*:" + seatId);
            for (String key : lockKeys) {
                Object value = redissonClient.getBucket(key).get();
                if (value != null && value.toString().startsWith(prefix)) {
                    redissonClient.getBucket(key).delete();
                }
            }
        }
        log.info("删除用户锁座记录: userId={}, count={}", userId, seatIds.size());
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

        // 写入场次座位集合
        String sessionSeatsKey = RedisKey.sessionSeatsKey(sessionId);
        redissonClient.getSet(sessionSeatsKey).delete();
        redissonClient.getSet(sessionSeatsKey).addAll(seatIds);

        log.info("初始化场次数据完成: sessionId={}, totalSeats={}", sessionId, totalSeats);
    }

    @Override
    public void clearSessionCache(Long sessionId) {
        String sessionIdStr = String.valueOf(sessionId);

        // 删除座位布局缓存
        String layoutKey = "session:layout:" + sessionIdStr;
        redissonClient.getBucket(layoutKey).delete();

        // 删除场次座位集合
        String sessionSeatsKey = RedisKey.sessionSeatsKey(sessionId);
        redissonClient.getSet(sessionSeatsKey).delete();

        // 删除售罄标志
        String soldoutKey = RedisKey.sessionSoldoutKey(sessionId);
        redissonClient.getBucket(soldoutKey).delete();

        // 删除所有座位状态和价格（使用scan）
        // 兼容迁移：顺手清理旧版 seat:{sessionId}:{seatId} 结构，避免初始化后仍有遗留脏数据。
        String legacySeatPattern = "seat:" + sessionIdStr + ":*";
        String seatStatePattern = RedisKey.SEAT_STATE_PREFIX + sessionIdStr + ":*";
        String seatLockPattern = RedisKey.SEAT_LOCK_PREFIX + sessionIdStr + ":*";
        String pricePattern = RedisKey.PRICE_PREFIX + sessionIdStr + ":*";

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
}

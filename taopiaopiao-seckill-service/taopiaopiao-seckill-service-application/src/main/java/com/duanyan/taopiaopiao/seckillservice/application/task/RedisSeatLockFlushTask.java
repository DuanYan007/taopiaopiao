package com.duanyan.taopiaopiao.seckillservice.application.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.redis.constants.RedisKey;
import com.duanyan.taopiaopiao.common.redis.model.RedisLockOrderData;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.seckillservice.application.mapper.SeatLockMapper;
import com.duanyan.taopiaopiao.seckillservice.application.model.SeatPositionRecord;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.SeatLock;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockOrderStatus;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSeatLockFlushTask {

    private static final int BATCH_SIZE = 100;

    private final RedissonClient redissonClient;
    private final RedisService redisService;
    private final SeatLockMapper seatLockMapper;

    @Scheduled(fixedDelay = 5000L, initialDelay = 7000L)
    public void flush() {
        for (String redisKey : listLockOrderKeys()) {
            try {
                String orderNo = redisKey.substring(RedisKey.LOCK_ORDER_PREFIX.length());
                RedisLockOrderData lockOrder = redisService.getLockOrder(orderNo);
                if (lockOrder == null || lockOrder.getSeatIds() == null || lockOrder.getSeatIds().isEmpty()) {
                    continue;
                }
                flushSeatLocks(lockOrder);
            } catch (Exception e) {
                log.error("刷盘 seat_locks 失败: redisKey={}", redisKey, e);
            }
        }
    }

    private void flushSeatLocks(RedisLockOrderData lockOrder) {
        Long count = seatLockMapper.selectCount(
                new LambdaQueryWrapper<SeatLock>()
                        .eq(SeatLock::getOrderNo, lockOrder.getOrderNo())
        );

        if (count == null || count == 0) {
            Map<String, int[]> seatPositionMap = loadSeatPositions(lockOrder.getSessionId(), lockOrder.getSeatIds());
            List<SeatLock> seatLocks = lockOrder.getSeatIds().stream()
                    .map(seatId -> {
                        int[] seatPosition = seatPositionMap.getOrDefault(seatId, new int[]{0, 0});
                        return SeatLock.builder()
                            .sessionId(lockOrder.getSessionId())
                            .userId(lockOrder.getUserId())
                            .seatId(seatId)
                            .lockId(lockOrder.getLockId())
                            .seatRow(seatPosition[0])
                            .seatCol(seatPosition[1])
                            .lockTime(lockOrder.getCreatedAt() == null ? System.currentTimeMillis()
                                    : lockOrder.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                            .expireTime(lockOrder.getExpireTime() == null ? null
                                    : lockOrder.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                            .status(resolveSeatLockStatus(lockOrder.getStatus()))
                            .orderNo(lockOrder.getOrderNo())
                            .build();
                    })
                    .toList();
            seatLockMapper.batchInsert(seatLocks);
            return;
        }

        if (LockOrderStatus.PAID.getCode().equals(lockOrder.getStatus())) {
            for (String seatId : lockOrder.getSeatIds()) {
                seatLockMapper.markAsPaid(lockOrder.getSessionId(), lockOrder.getUserId(), seatId, lockOrder.getLockId(), lockOrder.getOrderNo());
            }
            return;
        }

        if (LockOrderStatus.TIMEOUT.getCode().equals(lockOrder.getStatus())
                || LockOrderStatus.CANCELLED.getCode().equals(lockOrder.getStatus())
                || LockOrderStatus.FAILED.getCode().equals(lockOrder.getStatus())) {
            Integer targetStatus = LockOrderStatus.TIMEOUT.getCode().equals(lockOrder.getStatus())
                    ? LockStatus.EXPIRED.getCode()
                    : LockStatus.RELEASED.getCode();
            for (String seatId : lockOrder.getSeatIds()) {
                seatLockMapper.updateStatusByLock(
                        lockOrder.getSessionId(),
                        lockOrder.getUserId(),
                        seatId,
                        lockOrder.getLockId(),
                        LockStatus.LOCKED.getCode(),
                        targetStatus
                );
            }
        }
    }

    private int resolveSeatLockStatus(Integer lockOrderStatus) {
        if (LockOrderStatus.PAID.getCode().equals(lockOrderStatus)) {
            return LockStatus.PAID.getCode();
        }
        if (LockOrderStatus.TIMEOUT.getCode().equals(lockOrderStatus)) {
            return LockStatus.EXPIRED.getCode();
        }
        if (LockOrderStatus.CANCELLED.getCode().equals(lockOrderStatus)
                || LockOrderStatus.FAILED.getCode().equals(lockOrderStatus)) {
            return LockStatus.RELEASED.getCode();
        }
        return LockStatus.LOCKED.getCode();
    }

    private Map<String, int[]> loadSeatPositions(Long sessionId, List<String> seatIds) {
        Map<String, int[]> result = new HashMap<>();
        if (seatIds == null || seatIds.isEmpty()) {
            return result;
        }
        for (SeatPositionRecord record : seatLockMapper.selectSeatPositions(sessionId, seatIds)) {
            result.put(record.getSeatId(), new int[]{
                    record.getSeatRow() == null ? 0 : record.getSeatRow(),
                    record.getSeatCol() == null ? 0 : record.getSeatCol()
            });
        }
        return result;
    }

    private List<String> listLockOrderKeys() {
        Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(RedisKey.LOCK_ORDER_PREFIX + "*");
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            result.add(key);
            if (result.size() >= BATCH_SIZE) {
                break;
            }
        }
        return result;
    }
}

package com.duanyan.taopiaopiao.seckillservice.application.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.redis.constants.RedisKey;
import com.duanyan.taopiaopiao.seckillservice.application.mapper.LockOrderMapper;
import com.duanyan.taopiaopiao.seckillservice.application.monitor.SeckillFlowMetrics;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.LockOrder;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.PendingResult;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillBacklogSnapshotTask {

    private static final String STREAM_GROUP = "lock-accepted-bridge";

    private final LockOrderMapper lockOrderMapper;
    private final SeckillFlowMetrics metrics;
    private final RedissonClient redissonClient;

    @Scheduled(fixedDelay = 10000L, initialDelay = 10000L)
    public void snapshot() {
        long lockedCount = countLockOrderByStatus(LockOrderStatus.LOCKED.getCode());
        long orderCreatingCount = countLockOrderByStatus(LockOrderStatus.ORDER_CREATING.getCode());
        long orderCreatedCount = countLockOrderByStatus(LockOrderStatus.ORDER_CREATED.getCode());
        long oldestLockedAgeSeconds = getOldestLockOrderAgeSeconds(LockOrderStatus.LOCKED.getCode());
        long oldestOrderCreatingAgeSeconds = getOldestLockOrderAgeSeconds(LockOrderStatus.ORDER_CREATING.getCode());
        long redisExpireQueueCount = getRedisExpireQueueCount();
        long redisLockAcceptedStreamSize = getRedisLockAcceptedStreamSize();
        long redisLockAcceptedPendingCount = getRedisLockAcceptedPendingCount();

        log.info(
                "seckill-backlog-snapshot lockOrdersLockedCount={} lockOrdersOrderCreatingCount={} lockOrdersOrderCreatedCount={} " +
                        "oldestLockedAgeSeconds={} oldestOrderCreatingAgeSeconds={} redisExpireQueueCount={} redisLockAcceptedStreamSize={} redisLockAcceptedPendingCount={} " +
                        "bridgeSentTotal={} bridgeFailedTotal={} recoveryRetryTotal={} recoveryTimeoutTotal={} recoveryOrderCreatedCatchupTotal={}",
                lockedCount,
                orderCreatingCount,
                orderCreatedCount,
                oldestLockedAgeSeconds,
                oldestOrderCreatingAgeSeconds,
                redisExpireQueueCount,
                redisLockAcceptedStreamSize,
                redisLockAcceptedPendingCount,
                metrics.getBridgeSentTotal(),
                metrics.getBridgeFailedTotal(),
                metrics.getRecoveryRetryTotal(),
                metrics.getRecoveryTimeoutTotal(),
                metrics.getRecoveryOrderCreatedCatchupTotal()
        );
    }

    private long countLockOrderByStatus(Integer status) {
        Long count = lockOrderMapper.selectCount(
                new LambdaQueryWrapper<LockOrder>()
                        .eq(LockOrder::getStatus, status)
        );
        return count == null ? 0L : count;
    }

    private long getOldestLockOrderAgeSeconds(Integer status) {
        LockOrder lockOrder = lockOrderMapper.selectOne(
                new LambdaQueryWrapper<LockOrder>()
                        .eq(LockOrder::getStatus, status)
                        .orderByAsc(LockOrder::getUpdatedAt)
                        .last("LIMIT 1")
        );
        if (lockOrder == null || lockOrder.getUpdatedAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(lockOrder.getUpdatedAt(), LocalDateTime.now()).getSeconds());
    }

    private long getRedisExpireQueueCount() {
        long total = 0L;
        for (Long sessionId : listInitializedSessions()) {
            RScoredSortedSet<String> expireSet = redissonClient.getScoredSortedSet(RedisKey.lockExpireKey(sessionId), StringCodec.INSTANCE);
            total += expireSet.size();
        }
        return total;
    }

    private long getRedisLockAcceptedStreamSize() {
        long total = 0L;
        for (Long sessionId : listInitializedSessions()) {
            RStream<String, String> stream = redissonClient.getStream(RedisKey.lockAcceptedStreamKey(sessionId), StringCodec.INSTANCE);
            total += stream.size();
        }
        return total;
    }

    private long getRedisLockAcceptedPendingCount() {
        long total = 0L;
        for (Long sessionId : listInitializedSessions()) {
            try {
                PendingResult pending = redissonClient
                        .getStream(RedisKey.lockAcceptedStreamKey(sessionId), StringCodec.INSTANCE)
                        .getPendingInfo(STREAM_GROUP);
                if (pending != null) {
                    total += pending.getTotal();
                }
            } catch (RedisException e) {
                if (e.getMessage() == null || !e.getMessage().contains("NOGROUP")) {
                    throw e;
                }
            }
        }
        return total;
    }

    private List<Long> listInitializedSessions() {
        Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(
                RedisKey.SESSION_META_PREFIX + "*" + RedisKey.SESSION_META_SUFFIX
        );
        List<Long> sessionIds = new ArrayList<>();
        String prefix = RedisKey.SESSION_META_PREFIX;
        String suffix = RedisKey.SESSION_META_SUFFIX;
        for (String key : keys) {
            if (!key.startsWith(prefix) || !key.endsWith(suffix)) {
                continue;
            }
            String idPart = key.substring(prefix.length(), key.length() - suffix.length());
            try {
                sessionIds.add(Long.valueOf(idPart));
            } catch (NumberFormatException ignored) {
                log.warn("忽略无法解析的场次元数据 key: {}", key);
            }
        }
        return sessionIds;
    }
}

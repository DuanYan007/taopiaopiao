package com.duanyan.taopiaopiao.seckillservice.application.task;

import com.duanyan.taopiaopiao.common.redis.constants.RedisKey;
import com.duanyan.taopiaopiao.common.redis.model.RedisLockOrderData;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.seckillservice.application.client.OrderInternalClient;
import com.duanyan.taopiaopiao.seckillservice.application.monitor.SeckillFlowMetrics;
import com.duanyan.taopiaopiao.seckillservice.application.service.impl.SeckillServiceImpl;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockOrderStatus;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
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
public class RedisLockOrderRecoveryTask {

    private static final int BATCH_SIZE = 100;
    private static final int RECOVERY_DELAY_SECONDS = 5;

    private final RedissonClient redissonClient;
    private final RedisService redisService;
    private final OrderInternalClient orderInternalClient;
    private final SeckillFlowMetrics metrics;
    private final SeckillServiceImpl seckillService;

    @Scheduled(fixedDelay = 2000L, initialDelay = 2000L)
    public void recover() {
        for (Long sessionId : listInitializedSessions()) {
            try {
                recoverSession(sessionId);
            } catch (Exception e) {
                log.error("Redis 锁单恢复异常: sessionId={}", sessionId, e);
            }
        }
    }

    private void recoverSession(Long sessionId) {
        RScoredSortedSet<String> expireIndex = redissonClient.getScoredSortedSet(RedisKey.lockExpireKey(sessionId), StringCodec.INSTANCE);
        List<String> orderNos = new ArrayList<>(expireIndex.valueRange(0, true, Double.MAX_VALUE, true, 0, BATCH_SIZE));
        if (orderNos.isEmpty()) {
            return;
        }

        for (String orderNo : orderNos) {
            try {
                handleOrder(sessionId, expireIndex, orderNo);
            } catch (Exception e) {
                log.error("恢复 Redis 锁单失败: sessionId={}, orderNo={}", sessionId, orderNo, e);
            }
        }
    }

    private void handleOrder(Long sessionId, RScoredSortedSet<String> expireIndex, String orderNo) {
        RedisLockOrderData lockOrder = redisService.getLockOrder(orderNo);
        if (lockOrder == null) {
            expireIndex.remove(orderNo);
            return;
        }

        Integer status = lockOrder.getStatus();
        if (status == null) {
            return;
        }

        if (isTerminal(status) || LockOrderStatus.ORDER_CREATED.getCode().equals(status)) {
            expireIndex.remove(orderNo);
            return;
        }

        if (!isRecoverable(status) || !isStale(lockOrder.getUpdatedAt())) {
            return;
        }

        boolean orderExists = formalOrderExists(orderNo);
        if (orderExists) {
            boolean updated = seckillService.markLockOrderOrderCreated(orderNo);
            expireIndex.remove(orderNo);
            if (updated) {
                metrics.recordRecoveryOrderCreatedCatchup();
                log.info("Redis 恢复命中已存在正式订单，锁单状态已收敛: orderNo={}", orderNo);
            }
            return;
        }

        if (lockOrder.getExpireTime() != null && !lockOrder.getExpireTime().isAfter(LocalDateTime.now())) {
            seckillService.releaseSeats(
                    lockOrder.getSessionId(),
                    lockOrder.getUserId(),
                    lockOrder.getLockId(),
                    lockOrder.getSeatIds(),
                    LockStatus.EXPIRED
            );
            seckillService.markLockOrderReleased(orderNo, LockOrderStatus.TIMEOUT, "REDIS_RECOVERY_TIMEOUT");
            metrics.recordRecoveryTimeout();
            log.warn("Redis 恢复命中已过期未建单锁单，已释放: orderNo={}", orderNo);
        }
    }

    private boolean formalOrderExists(String orderNo) {
        Result<Boolean> result = orderInternalClient.exists(orderNo);
        return result != null && result.isSuccess() && Boolean.TRUE.equals(result.getData());
    }

    private boolean isRecoverable(Integer status) {
        return LockOrderStatus.LOCKED.getCode().equals(status)
                || LockOrderStatus.ORDER_CREATING.getCode().equals(status);
    }

    private boolean isTerminal(Integer status) {
        return LockOrderStatus.PAID.getCode().equals(status)
                || LockOrderStatus.TIMEOUT.getCode().equals(status)
                || LockOrderStatus.CANCELLED.getCode().equals(status)
                || LockOrderStatus.FAILED.getCode().equals(status);
    }

    private boolean isStale(LocalDateTime updatedAt) {
        if (updatedAt == null) {
            return true;
        }
        return !updatedAt.isAfter(LocalDateTime.now().minusSeconds(RECOVERY_DELAY_SECONDS));
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

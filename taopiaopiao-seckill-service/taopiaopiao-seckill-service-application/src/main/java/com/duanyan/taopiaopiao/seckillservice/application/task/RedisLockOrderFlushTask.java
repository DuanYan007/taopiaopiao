package com.duanyan.taopiaopiao.seckillservice.application.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.redis.constants.RedisKey;
import com.duanyan.taopiaopiao.common.redis.model.RedisLockOrderData;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.seckillservice.application.mapper.LockOrderMapper;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.LockOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockOrderFlushTask {

    private static final int BATCH_SIZE = 100;

    private final RedissonClient redissonClient;
    private final RedisService redisService;
    private final LockOrderMapper lockOrderMapper;

    @Scheduled(fixedDelay = 5000L, initialDelay = 5000L)
    public void flush() {
        for (String redisKey : listLockOrderKeys()) {
            try {
                String orderNo = redisKey.substring(RedisKey.LOCK_ORDER_PREFIX.length());
                RedisLockOrderData redisLockOrder = redisService.getLockOrder(orderNo);
                if (redisLockOrder == null) {
                    continue;
                }
                upsertLockOrder(redisLockOrder);
            } catch (Exception e) {
                log.error("刷盘 lock_orders 失败: redisKey={}", redisKey, e);
            }
        }
    }

    private void upsertLockOrder(RedisLockOrderData redisLockOrder) {
        LockOrder existing = lockOrderMapper.selectOne(
                new LambdaQueryWrapper<LockOrder>()
                        .eq(LockOrder::getOrderNo, redisLockOrder.getOrderNo())
                        .last("LIMIT 1")
        );

        if (existing == null) {
            lockOrderMapper.insert(LockOrder.builder()
                    .lockId(redisLockOrder.getLockId())
                    .orderNo(redisLockOrder.getOrderNo())
                    .requestId(redisLockOrder.getRequestId())
                    .userId(redisLockOrder.getUserId())
                    .sessionId(redisLockOrder.getSessionId())
                    .eventId(redisLockOrder.getEventId())
                    .seatIds(redisLockOrder.getSeatIds() == null ? List.of() : redisLockOrder.getSeatIds())
                    .seatCount(redisLockOrder.getSeatCount())
                    .unitPrice(redisLockOrder.getUnitPrice())
                    .totalAmount(redisLockOrder.getTotalAmount())
                    .status(redisLockOrder.getStatus())
                    .expireTime(redisLockOrder.getExpireTime())
                    .failReason(redisLockOrder.getFailReason())
                    .createdAt(redisLockOrder.getCreatedAt())
                    .updatedAt(redisLockOrder.getUpdatedAt())
                    .build());
            return;
        }

        existing.setLockId(redisLockOrder.getLockId());
        existing.setRequestId(redisLockOrder.getRequestId());
        existing.setUserId(redisLockOrder.getUserId());
        existing.setSessionId(redisLockOrder.getSessionId());
        existing.setEventId(redisLockOrder.getEventId());
        existing.setSeatIds(redisLockOrder.getSeatIds() == null ? List.of() : redisLockOrder.getSeatIds());
        existing.setSeatCount(redisLockOrder.getSeatCount());
        existing.setUnitPrice(redisLockOrder.getUnitPrice());
        existing.setTotalAmount(redisLockOrder.getTotalAmount());
        existing.setStatus(redisLockOrder.getStatus());
        existing.setExpireTime(redisLockOrder.getExpireTime());
        existing.setFailReason(redisLockOrder.getFailReason());
        existing.setUpdatedAt(redisLockOrder.getUpdatedAt());
        lockOrderMapper.updateById(existing);
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

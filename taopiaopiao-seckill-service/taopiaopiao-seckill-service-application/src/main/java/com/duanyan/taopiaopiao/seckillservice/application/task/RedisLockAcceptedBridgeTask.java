package com.duanyan.taopiaopiao.seckillservice.application.task;

import com.duanyan.taopiaopiao.common.mq.message.LockAcceptedMessage;
import com.duanyan.taopiaopiao.common.redis.constants.RedisKey;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.seckillservice.application.monitor.SeckillFlowMetrics;
import com.duanyan.taopiaopiao.seckillservice.application.producer.LockAcceptedProducer;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockOrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.PendingResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockAcceptedBridgeTask {

    private static final String GROUP_NAME = "lock-accepted-bridge";
    private static final int BATCH_SIZE = 100;
    private static final long PENDING_IDLE_MILLIS = 5000L;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final LockAcceptedProducer lockAcceptedProducer;
    private final RedisService redisService;
    private final SeckillFlowMetrics metrics;

    @Scheduled(fixedDelay = 1000L, initialDelay = 1000L)
    public void bridge() {
        for (Long sessionId : listInitializedSessions()) {
            try {
                ensureGroup(sessionId);
                consumeNewMessages(sessionId);
                consumePendingMessages(sessionId);
            } catch (Exception e) {
                log.error("处理锁座受理 Stream 失败: sessionId={}", sessionId, e);
            }
        }
    }

    private void ensureGroup(Long sessionId) {
        try {
            stream(sessionId).createGroup(
                    StreamCreateGroupArgs.name(GROUP_NAME)
                            .id(StreamMessageId.ALL)
                            .makeStream()
            );
        } catch (RedisException e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    private void consumeNewMessages(Long sessionId) throws Exception {
        Map<StreamMessageId, Map<String, String>> messages = stream(sessionId).readGroup(
                GROUP_NAME,
                consumerName(sessionId),
                StreamReadGroupArgs.neverDelivered()
                        .count(BATCH_SIZE)
                        .timeout(Duration.ofMillis(200))
        );
        processMessages(sessionId, messages);
    }

    private void consumePendingMessages(Long sessionId) throws Exception {
        PendingResult pending = stream(sessionId).getPendingInfo(GROUP_NAME);
        if (pending == null || pending.getTotal() <= 0) {
            return;
        }

        AutoClaimResult<String, String> result = stream(sessionId).autoClaim(
                GROUP_NAME,
                consumerName(sessionId),
                PENDING_IDLE_MILLIS,
                TimeUnit.MILLISECONDS,
                StreamMessageId.MIN,
                BATCH_SIZE
        );
        processMessages(sessionId, result == null ? null : result.getMessages());
    }

    private void processMessages(Long sessionId, Map<StreamMessageId, Map<String, String>> messages) throws Exception {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
            StreamMessageId messageId = entry.getKey();
            Map<String, String> body = entry.getValue();
            try {
                String payloadJson = body.get("payloadJson");
                if (payloadJson == null) {
                    throw new IllegalStateException("Stream 消息缺少 payloadJson");
                }

                LockAcceptedMessage message = objectMapper.readValue(payloadJson, LockAcceptedMessage.class);
                lockAcceptedProducer.send(message);
                redisService.transitionLockOrderStatus(
                        message.getOrderNo(),
                        List.of(LockOrderStatus.LOCKED.getCode(), LockOrderStatus.ORDER_CREATING.getCode()),
                        LockOrderStatus.ORDER_CREATING.getCode(),
                        "NOT_READY",
                        null,
                        false,
                        7200L
                );
                stream(sessionId).ack(GROUP_NAME, messageId);
                metrics.recordBridgeSent();
            } catch (Exception e) {
                metrics.recordBridgeFailed();
                log.error("桥接 Redis Stream -> RocketMQ 失败: sessionId={}, messageId={}", sessionId, messageId, e);
                throw e;
            }
        }
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

    private String consumerName(Long sessionId) {
        return "bridge-" + sessionId + "-" + redissonClient.getId();
    }

    private RStream<String, String> stream(Long sessionId) {
        return redissonClient.getStream(RedisKey.lockAcceptedStreamKey(sessionId), StringCodec.INSTANCE);
    }
}

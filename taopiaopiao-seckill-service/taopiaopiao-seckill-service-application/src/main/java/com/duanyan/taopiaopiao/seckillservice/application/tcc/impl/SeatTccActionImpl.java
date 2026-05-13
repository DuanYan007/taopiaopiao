package com.duanyan.taopiaopiao.seckillservice.application.tcc.impl;

import com.duanyan.taopiaopiao.common.exception.BusinessException;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.seckillservice.application.support.TestFailPointSupport;
import com.duanyan.taopiaopiao.seckillservice.application.tcc.SeatTccAction;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatTccActionImpl implements SeatTccAction {

    private static final int CANCEL_MARKER_EXPIRE_SECONDS = 15;

    private final RedisService redisService;
    private final TestFailPointSupport testFailPointSupport;

    @Override
    public boolean tryReserve(BusinessActionContext actionContext,
                              String orderNo,
                              Long userId,
                              Long sessionId,
                              Long eventId,
                              List<String> seatIds,
                              Integer expireSeconds) {
        BusinessActionContextUtil.addContext(buildActionContext(orderNo, userId, sessionId, eventId, seatIds, expireSeconds));
        String xid = RootContext.getXID();
        int code = redisService.tryReserveSeatsTcc(
                sessionId,
                eventId,
                userId,
                orderNo,
                xid,
                seatIds,
                expireSeconds,
                expireSeconds
        );
        if (code == 0 || code == 5) {
            testFailPointSupport.failOnce("SEAT_TRY_AFTER_REDIS_LOCK");
        }
        if (code == 0 || code == 5) {
            return true;
        }
        throw new BusinessException(resolveTryCode(code), resolveTryMessage(code));
    }

    @Override
    public boolean confirmReserve(BusinessActionContext actionContext) {
        String orderNo = getStringValue(actionContext, "orderNo");
        Long sessionId = getLongValue(actionContext, "sessionId");
        Long userId = getLongValue(actionContext, "userId");
        List<String> seatIds = getSeatIds(actionContext);
        if (orderNo == null || sessionId == null || userId == null || seatIds == null || seatIds.isEmpty()) {
            log.warn("TCC Confirm 缺少上下文，按空确认跳过: xid={}, actionContext={}",
                    actionContext == null ? null : actionContext.getXid(),
                    actionContext == null ? null : actionContext.getActionContext());
            return true;
        }
        int code = redisService.confirmReserveSeatsTcc(
                sessionId,
                userId,
                orderNo,
                actionContext.getXid(),
                seatIds
        );
        if (code == 0 || code == 1) {
            testFailPointSupport.failOnce("SEAT_CONFIRM_AFTER_REDIS_CONFIRM");
        }
        return code == 0 || code == 1;
    }

    @Override
    public boolean cancelReserve(BusinessActionContext actionContext) {
        String orderNo = getStringValue(actionContext, "orderNo");
        Long sessionId = getLongValue(actionContext, "sessionId");
        Long userId = getLongValue(actionContext, "userId");
        List<String> seatIds = getSeatIds(actionContext);
        if (orderNo == null || sessionId == null || userId == null || seatIds == null || seatIds.isEmpty()) {
            log.warn("TCC Cancel 缺少上下文，按空回滚跳过: xid={}, actionContext={}",
                    actionContext == null ? null : actionContext.getXid(),
                    actionContext == null ? null : actionContext.getActionContext());
            return true;
        }
        int code = redisService.cancelReserveSeatsTcc(
                sessionId,
                userId,
                orderNo,
                actionContext.getXid(),
                seatIds,
                CANCEL_MARKER_EXPIRE_SECONDS
        );
        if (code == 0 || code == 1 || code == 2) {
            testFailPointSupport.failOnce("SEAT_CANCEL_AFTER_REDIS_CANCEL");
        }
        return code == 0 || code == 1 || code == 2;
    }

    @SuppressWarnings("unchecked")
    private List<String> getSeatIds(BusinessActionContext actionContext) {
        if (actionContext == null) {
            return null;
        }
        Object value = actionContext.getActionContext("seatIds");
        if (value instanceof List<?> listValue) {
            return listValue.stream().map(String::valueOf).toList();
        }
        if (value instanceof String stringValue) {
            return parseSeatIds(stringValue);
        }
        return null;
    }

    private List<String> parseSeatIds(String value) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of(trimmed);
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        String[] parts = body.split(",");
        List<String> seatIds = new ArrayList<>(parts.length);
        for (String part : parts) {
            String item = part.trim();
            if (item.startsWith("\"") && item.endsWith("\"") && item.length() >= 2) {
                item = item.substring(1, item.length() - 1);
            }
            if (!item.isEmpty()) {
                seatIds.add(item);
            }
        }
        return seatIds;
    }

    private Long getLongValue(BusinessActionContext actionContext, String key) {
        if (actionContext == null) {
            return null;
        }
        Object value = actionContext.getActionContext(key);
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private String getStringValue(BusinessActionContext actionContext, String key) {
        if (actionContext == null) {
            return null;
        }
        Object value = actionContext.getActionContext(key);
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> buildActionContext(String orderNo,
                                                   Long userId,
                                                   Long sessionId,
                                                   Long eventId,
                                                   List<String> seatIds,
                                                   Integer expireSeconds) {
        Map<String, Object> actionContext = new LinkedHashMap<>();
        actionContext.put("orderNo", orderNo);
        actionContext.put("userId", userId);
        actionContext.put("sessionId", sessionId);
        actionContext.put("eventId", eventId);
        actionContext.put("seatIds", seatIds);
        actionContext.put("expireSeconds", expireSeconds);
        return actionContext;
    }

    private Integer resolveTryCode(int code) {
        return switch (code) {
            case 1 -> 1;
            case 2, 7 -> 2;
            case 3, 6 -> 3;
            case 4 -> 4;
            default -> 8;
        };
    }

    private String resolveTryMessage(int code) {
        return switch (code) {
            case 1 -> "座位不存在";
            case 2, 7 -> "座位已被锁定或售出";
            case 3, 6 -> "您已锁定或购买了该座位";
            case 4 -> "场次与演出信息不匹配";
            default -> "系统错误";
        };
    }
}

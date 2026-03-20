package com.duanyan.taopiaopiao.seckillservice.application.service.impl;

import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.common.redis.service.RedisService;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatResponse;
import com.duanyan.taopiaopiao.seckillservice.application.client.OrderClient;
import com.duanyan.taopiaopiao.seckillservice.application.client.SessionClient;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.CreateOrderDTO;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.OrderDTO;
import com.duanyan.taopiaopiao.seckillservice.application.client.dto.SessionDTO;
import com.duanyan.taopiaopiao.seckillservice.application.mapper.SeatLockMapper;
import com.duanyan.taopiaopiao.seckillservice.application.service.SeckillService;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.SeatLock;
import com.duanyan.taopiaopiao.seckillservice.domain.enums.LockStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeckillServiceImpl implements SeckillService {

    private final RedisService redisService;
    private final SeatLockMapper seatLockMapper;
    private final SessionClient sessionClient;
    private final OrderClient orderClient;

    @Override
    @Transactional
    public LockSeatResponse lockSeats(LockSeatRequest request) {
        Long sessionId = request.getSessionId();
        Long userId = request.getUserId();
        List<String> seatIds = request.getSeatIds();
        Integer expireSeconds = request.getExpireSeconds() != null ? request.getExpireSeconds() : 300;

        String lockId = UUID.randomUUID().toString().replace("-", "");
        long expireTime = System.currentTimeMillis() + expireSeconds * 1000L;
        // 1. 锁定座位
        int code = redisService.lockSeats(sessionId, userId, seatIds, expireSeconds);

        if (code == 0) {
            // 先插入 seat_locks 记录，orderNo 暂时为空
            for (String seatId : seatIds) {
                SeatLock seatLock = SeatLock.builder()
                        .sessionId(sessionId)
                        .userId(userId)
                        .seatId(seatId)
                        .seatRow(0)
                        .seatCol(0)
                        .lockTime(System.currentTimeMillis())
                        .expireTime(expireTime)
                        .status(LockStatus.LOCKED.getCode())
                        .orderNo(null)
                        .build();
                seatLockMapper.insert(seatLock);
            }
            log.info("锁座成功: sessionId={}, userId={}, lockId={}", sessionId, userId, lockId);

            // 调用订单服务创建待支付订单
            String orderNo = null;
            try {
                // 获取场次信息（获取价格）
                Result<SessionDTO> sessionResult = sessionClient.getSessionById(sessionId);
                if (sessionResult != null && sessionResult.getData() != null) {
                    SessionDTO session = sessionResult.getData();

                    // 计算总金额
                    BigDecimal unitPrice = session.getPrice() != null ? session.getPrice() : BigDecimal.ZERO;
                    BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(seatIds.size()));

                    // 创建待支付订单
                    CreateOrderDTO createOrderDTO = CreateOrderDTO.builder()
                            .userId(userId)
                            .sessionId(sessionId)
                            .eventId(session.getEventId())
                            .seatIds(seatIds)
                            .seatCount(seatIds.size())
                            .unitPrice(unitPrice)
                            .totalAmount(totalAmount)
                            .build();

                    Result<OrderDTO> orderResult = orderClient.createPendingOrder(createOrderDTO);
                    if (orderResult != null && orderResult.isSuccess() && orderResult.getData() != null) {
                        orderNo = orderResult.getData().getOrderNo();
                        // 更新 seat_locks 的 orderNo
                        for (String seatId : seatIds) {
                            seatLockMapper.updateOrderNo(sessionId, userId, seatId, orderNo);
                        }
                        log.info("创建待支付订单成功: orderNo={}, userId={}, sessionId={}", orderNo, userId, sessionId);
                    } else {
                        log.error("创建待支付订单失败: sessionId={}, userId={}", sessionId, userId);
                        // 订单创建失败，需要回滚锁座
                        releaseSeatsInternal(sessionId, userId, seatIds);
                        return LockSeatResponse.builder()
                                .success(false)
                                .code(4)
                                .message("创建订单失败，请重试")
                                .build();
                    }
                } else {
                    log.error("获取场次信息失败: sessionId={}", sessionId);
                    // 获取场次信息失败，需要回滚锁座
                    releaseSeatsInternal(sessionId, userId, seatIds);
                    return LockSeatResponse.builder()
                            .success(false)
                            .code(5)
                            .message("获取场次信息失败，请重试")
                            .build();
                }
            } catch (Exception e) {
                log.error("调用订单服务异常: sessionId={}, userId={}", sessionId, userId, e);
                // 调用订单服务异常，需要回滚锁座
                releaseSeatsInternal(sessionId, userId, seatIds);
                return LockSeatResponse.builder()
                        .success(false)
                        .code(6)
                        .message("系统异常，请重试")
                        .build();
            }

            return LockSeatResponse.builder()
                    .success(true)
                    .code(0)
                    .message("锁座成功")
                    .lockedSeats(seatIds)
                    .lockId(lockId)
                    .orderNo(orderNo)
                    .build();
        }

        String message = switch (code) {
            case 1 -> "座位不存在";
            case 2 -> "座位已被锁定或售出";
            case 3 -> "您已锁定或购买了该座位";
            default -> "系统错误";
        };

        log.warn("锁座失败: code={}, message={}", code, message);
        return LockSeatResponse.builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }

    /**
     * 内部方法：释放座位（不对外暴露）
     */
    private void releaseSeatsInternal(Long sessionId, Long userId, List<String> seatIds) {
        try {
            redisService.unlockSeats(sessionId, userId, seatIds);
            for (String seatId : seatIds) {
                seatLockMapper.updateStatus(sessionId, userId, seatId, LockStatus.RELEASED.getCode());
            }
            log.info("释放座位成功: sessionId={}, userId={}, count={}", sessionId, userId, seatIds.size());
        } catch (Exception e) {
            log.error("释放座位异常: sessionId={}, userId={}", sessionId, userId, e);
        }
    }

    /**
     * 内部方法：标记座位已支付（供订单服务调用）
     */
    @Transactional
    public Integer markSeatLocksPaid(String orderNo, Long sessionId, Long userId, List<String> seatIds) {
        int count = 0;
        for (String seatId : seatIds) {
            int updated = seatLockMapper.markAsPaid(sessionId, userId, seatId, orderNo);
            count += updated;
        }
        log.info("标记座位锁定已支付: orderNo={}, count={}", orderNo, count);
        return count;
    }

    /**
     * 内部方法：释放座位（供订单服务调用，用于取消/超时）
     */
    @Transactional
    public Integer releaseSeats(Long sessionId, Long userId, List<String> seatIds) {
        releaseSeatsInternal(sessionId, userId, seatIds);
        return seatIds.size();
    }
}

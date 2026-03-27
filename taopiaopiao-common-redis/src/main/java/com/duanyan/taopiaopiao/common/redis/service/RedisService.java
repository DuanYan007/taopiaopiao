package com.duanyan.taopiaopiao.common.redis.service;

import com.duanyan.taopiaopiao.common.redis.constants.SeatStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Redis 服务接口
 *
 * @author duanyan
 * @since 1.0.0
 */
public interface RedisService {

    /**
     * 初始化场次座位数据
     *
     * @param sessionId 场次ID
     * @param seatIds   座位ID列表 (格式: "row:col")
     */
    void initSessionSeats(Long sessionId, List<String> seatIds);

    /**
     * 初始化场次座位数据
     *
     * @param sessionId 场次ID
     * @param seatIds   座位ID列表 (格式: "row:col")
     */
    List<BigDecimal> getSeatsPrice(Long sessionId, List<String> seatIds);

    /**
     * 锁定座位
     *
     * @param sessionId    场次ID
     * @param userId       用户ID
     * @param seatIds      座位ID列表
     * @param expireSeconds 过期时间（秒）
     * @return 锁座结果码: 0=成功, 1=座位不存在, 2=座位不可用, 3=重复购票
     */
    int lockSeats(Long sessionId, Long userId, List<String> seatIds, int expireSeconds);

    /**
     * 释放座位
     *
     * @param sessionId 场次ID
     * @param userId    用户ID
     * @param seatIds   座位ID列表
     * @return 实际释放的座位数量
     */
    int unlockSeats(Long sessionId, Long userId, List<String> seatIds);

    /**
     * 确认购买（将锁定状态改为已售出）
     *
     * @param sessionId 场次ID
     * @param userId    用户ID
     * @param seatIds   座位ID列表
     * @return true=成功, false=失败（无权操作）
     */
    boolean confirmPurchase(Long sessionId, Long userId, List<String> seatIds);

    /**
     * 获取座位状态
     *
     * @param sessionId 场次ID
     * @param seatId    座位ID
     * @return 座位状态
     */
    SeatStatus getSeatStatus(Long sessionId, String seatId);

    /**
     * 设置座位状态
     *
     * @param sessionId 场次ID
     * @param seatId    座位ID
     * @param status    状态
     */
    void setSeatStatus(Long sessionId, String seatId, SeatStatus status);

    /**
     * 删除场次相关数据
     *
     * @param sessionId 场次ID
     */
    void clearSessionData(Long sessionId);

    /**
     * 获取用户锁定的座位数量
     *
     * @param userId 用户ID
     * @return 锁定的座位数量
     */
    long getUserLockedSeatCount(Long userId);
}

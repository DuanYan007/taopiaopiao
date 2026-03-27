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

    /**
     * 删除用户锁座记录（支付成功后调用）
     *
     * @param userId 用户ID
     * @param seatIds 座位ID列表
     */
    void removeUserLocks(Long userId, List<String> seatIds);

    /**
     * 获取场次座位布局（含状态）
     *
     * @param sessionId 场次ID
     * @return 布局数据Map，key为field(meta或area:N)，value为JSON字符串
     *         如果缓存不存在则返回null
     */
    java.util.Map<Object, Object> getSessionLayout(Long sessionId);

    /**
     * 初始化场次缓存数据
     *
     * @param sessionId 场次ID
     * @param seatIds 座位ID列表
     * @param areaPrices 座位价格列表（索引对应seatIds）
     */
    void initSessionData(Long sessionId, java.util.List<String> seatIds, java.util.List<Integer> areaPrices);

    /**
     * 清除场次所有缓存数据
     *
     * @param sessionId 场次ID
     */
    void clearSessionCache(Long sessionId);

    /**
     * 保存座位布局缓存
     *
     * @param sessionId 场次ID
     * @param metaJson 元数据JSON字符串
     * @param areaJsonMap 各区域座位数据JSON字符串Map，key为"area:0", "area:1"等
     */
    void saveSessionLayout(Long sessionId, String metaJson, java.util.Map<String, String> areaJsonMap);
}

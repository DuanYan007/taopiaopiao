package com.duanyan.taopiaopiao.common.redis.service;

import com.duanyan.taopiaopiao.common.redis.model.RedisLockOrderData;
import com.duanyan.taopiaopiao.common.redis.model.OrderProcessingCacheData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
     * @param seatIds   座位ID列表（seats 表主键ID的字符串形式）
     */
    List<BigDecimal> getSeatsPrice(Long sessionId, List<String> seatIds);

    int lockSeatsAndRecordOrder(Long sessionId,
                                Long eventId,
                                Long userId,
                                String lockId,
                                String orderNo,
                                List<String> seatIds,
                                BigDecimal unitPrice,
                                BigDecimal totalAmount,
                                int seatLockExpireSeconds,
                                int userLockExpireSeconds,
                                long lockOrderTtlSeconds,
                                long expireTimeMillis,
                                long createdAtMillis,
                                String requestId,
                                String payloadJson);

    /**
     * 释放座位
     *
     * @param sessionId 场次ID
     * @param userId    用户ID
     * @param seatIds   座位ID列表
     * @return 实际释放的座位数量
     */
    int unlockSeats(Long sessionId, Long userId, String lockId, List<String> seatIds);

    /**
     * 确认购买。
     * 校验锁归属后将座位状态写为已售出，并删除临时锁键。
     *
     * @param sessionId 场次ID
     * @param userId    用户ID
     * @param seatIds   座位ID列表
     * @return true=成功, false=失败（无权操作）
     */
    boolean confirmPurchase(Long sessionId, Long userId, String lockId, List<String> seatIds);

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

    /**
     * 保存场次快照元数据
     *
     * @param sessionId 场次ID
     * @param eventId 演出ID
     */
    void saveSessionMeta(Long sessionId, Long eventId);

    /**
     * 获取场次快照元数据
     *
     * @param sessionId 场次ID
     * @return 包含 eventId 的元数据，缓存不存在时返回 null
     */
    Map<String, Object> getSessionMeta(Long sessionId);

    /**
     * 批量获取座位当前有效状态。
     * 优先级：已售出(2) > 已锁定(1) > 可选(0)
     *
     * @param sessionId 场次ID
     * @param seatIds   座位ID列表
     * @return key=seatId, value=当前有效状态码
     */
    Map<String, Integer> getEffectiveSeatStatuses(Long sessionId, List<String> seatIds);

    void saveOrderProcessing(OrderProcessingCacheData data, long ttlSeconds);

    OrderProcessingCacheData getOrderProcessing(String orderNo);

    void deleteOrderProcessing(String orderNo);

    RedisLockOrderData getLockOrder(String orderNo);

    boolean transitionLockOrderStatus(String orderNo,
                                      List<Integer> expectedStatuses,
                                      Integer targetStatus,
                                      String paymentStatus,
                                      String failReason,
                                      boolean clearUserLockIndex,
                                      long ttlSeconds);
}

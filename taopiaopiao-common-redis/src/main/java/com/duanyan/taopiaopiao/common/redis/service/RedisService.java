package com.duanyan.taopiaopiao.common.redis.service;

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

    int tryReserveSeatsTcc(Long sessionId,
                           Long eventId,
                           Long userId,
                           String orderNo,
                           String xid,
                           List<String> seatIds,
                           int seatLockExpireSeconds,
                           int userLockExpireSeconds);

    int cancelReserveSeatsTcc(Long sessionId,
                              Long userId,
                              String orderNo,
                              String xid,
                              List<String> seatIds,
                              int cancelMarkerExpireSeconds);

    /**
     * TCC Confirm。
     * 校验临时锁归属后删除 seat:lock，并把 seat:state 置为 1（已下单未支付）。
     *
     * @param sessionId 场次ID
     * @param userId    用户ID
     * @param seatIds   座位ID列表
     */
    int confirmReserveSeatsTcc(Long sessionId,
                               Long userId,
                               String orderNo,
                               String xid,
                               List<String> seatIds);

    /**
     * 支付成功后把长期占座从 1 更新为 2。
     */
    boolean markSeatsPaid(Long sessionId, Long userId, String orderNo, List<String> seatIds);

    /**
     * 用户取消或超时取消后把长期占座从 1 释放为 0。
     */
    boolean releaseHeldSeats(Long sessionId, String orderNo, List<String> seatIds);

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

}

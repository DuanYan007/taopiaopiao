package com.duanyan.taopiaopiao.common.redis.constants;

/**
 * Redis Key 常量定义
 *
 * 命名规范：
 * - 座位状态: seat:state:{sessionId}:{seatId}
 * - 座位临时锁: seat:lock:{sessionId}:{seatId}
 * - 场次座位集合: session:{sessionId}:seats
 * - 售罄标志: session:{sessionId}:soldout
 * - 预约用户集合: reservation:{sessionId}:users
 *
 * @author duanyan
 * @since 1.0.0
 */
public class RedisKey {

    /**
     * 座位状态 Key 前缀
     * <p>完整格式: seat:state:{sessionId}:{seatId}
     * <p>值类型: String，值为状态码 (0=可选, 2=已售出)
     */
    public static final String SEAT_STATE_PREFIX = "seat:state:";

    /**
     * 座位临时锁 Key 前缀
     * <p>完整格式: seat:lock:{sessionId}:{seatId}
     * <p>值类型: String，值为 userId|lockId
     */
    public static final String SEAT_LOCK_PREFIX = "seat:lock:";
    /**
     * 座位价格 Key 前缀
     * <p>完整格式: price:{sessionId}:{seatId}
     * <p>值类型: String 价格
     */
    public static final String PRICE_PREFIX = "price:";

    /**
     * 场次座位集合 Key 前缀
     * <p>完整格式: session:{sessionId}:seats
     * <p>值类型: Set，存储所有座位ID
     */
    public static final String SESSION_SEATS_PREFIX = "session:";

    public static final String SESSION_SEATS_SUFFIX = ":seats";

    /**
     * 场次售罄标志 Key 前缀
     * <p>完整格式: session:{sessionId}:soldout
     * <p>值类型: String，值为 "1" 表示售罄
     */
    public static final String SESSION_SOLDOUT_PREFIX = "session:";

    public static final String SESSION_SOLDOUT_SUFFIX = ":soldout";

    /**
     * 预约用户集合 Key 前缀
     * <p>完整格式: reservation:{sessionId}:users
     * <p>值类型: Set，存储有资格的用户ID
     */
    public static final String RESERVATION_USERS_PREFIX = "reservation:";

    public static final String RESERVATION_USERS_SUFFIX = ":users";

    // ========== Key 构建方法 ==========


    /**
     * 构建座位状态 Key（使用座位ID）
     *
     * @param sessionId 场次ID
     * @param seatId    座位ID
     * @return seat:state:sessionId:seatId
     */
    public static String seatStateKey(Long sessionId, String seatId) {
        return SEAT_STATE_PREFIX + sessionId + ":" + seatId;
    }

    /**
     * 构建座位临时锁 Key（使用座位ID）
     *
     * @param sessionId 场次ID
     * @param seatId    座位ID
     * @return seat:lock:sessionId:seatId
     */
    public static String seatLockKey(Long sessionId, String seatId) {
        return SEAT_LOCK_PREFIX + sessionId + ":" + seatId;
    }

    /**
     * 构建座位状态 Key（使用座位ID）
     *
     * @param sessionId 场次ID
     * @param seatId    座位ID
     * @return seat:sessionId:seatIds
     */
    public static String seatPriceKey(Long sessionId, String seatId) {
        return PRICE_PREFIX + sessionId + ":" + seatId;
    }
    /**
     * 构建场次座位集合 Key
     *
     * @param sessionId 场次ID
     * @return session:sessionId:seats
     */
    public static String sessionSeatsKey(Long sessionId) {
        return SESSION_SEATS_PREFIX + sessionId + SESSION_SEATS_SUFFIX;
    }

    /**
     * 构建场次售罄标志 Key
     *
     * @param sessionId 场次ID
     * @return session:sessionId:soldout
     */
    public static String sessionSoldoutKey(Long sessionId) {
        return SESSION_SOLDOUT_PREFIX + sessionId + SESSION_SOLDOUT_SUFFIX;
    }

    /**
     * 构建预约用户集合 Key
     *
     * @param sessionId 场次ID
     * @return reservation:sessionId:users
     */
    public static String reservationUsersKey(Long sessionId) {
        return RESERVATION_USERS_PREFIX + sessionId + RESERVATION_USERS_SUFFIX;
    }

    private RedisKey() {
    }
}

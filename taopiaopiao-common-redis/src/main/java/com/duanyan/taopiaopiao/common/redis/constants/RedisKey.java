package com.duanyan.taopiaopiao.common.redis.constants;

/**
 * Redis Key 常量定义
 *
 * 命名规范：
 * - 座位状态: seat:state:{sessionId}:{seatId}
 * - 座位临时锁: seat:lock:{sessionId}:{seatId}
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

    public static final String SESSION_META_PREFIX = "session:";

    public static final String SESSION_META_SUFFIX = ":meta";

    public static final String ORDER_PROCESSING_PREFIX = "order:processing:";

    public static final String LOCK_ORDER_PREFIX = "lock:order:";

    public static final String LOCK_USER_PREFIX = "lock:user:";

    public static final String LOCK_EXPIRE_PREFIX = "lock:expire:";

    public static final String LOCK_ACCEPTED_STREAM_PREFIX = "stream:lock_accepted:";

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
     * 构建场次元数据 Key
     *
     * @param sessionId 场次ID
     * @return session:sessionId:meta
     */
    public static String sessionMetaKey(Long sessionId) {
        return SESSION_META_PREFIX + sessionId + SESSION_META_SUFFIX;
    }

    public static String orderProcessingKey(String orderNo) {
        return ORDER_PROCESSING_PREFIX + orderNo;
    }

    public static String lockOrderKey(String orderNo) {
        return LOCK_ORDER_PREFIX + orderNo;
    }

    public static String lockUserKey(Long sessionId, Long userId) {
        return LOCK_USER_PREFIX + sessionId + ":" + userId;
    }

    public static String lockExpireKey(Long sessionId) {
        return LOCK_EXPIRE_PREFIX + sessionId;
    }

    public static String lockAcceptedStreamKey(Long sessionId) {
        return LOCK_ACCEPTED_STREAM_PREFIX + sessionId;
    }

    private RedisKey() {
    }
}

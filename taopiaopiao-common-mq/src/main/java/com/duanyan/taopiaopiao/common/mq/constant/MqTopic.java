package com.duanyan.taopiaopiao.common.mq.constant;

/**
 * RocketMQ Topic 常量定义
 *
 * @author duanyan
 * @since 1.0.0
 */
public class MqTopic {

    /**
     * 支付 Topic
     */
    public static final String PAYMENT_TOPIC = "TPP_PAYMENT_TOPIC";

    /**
     * 订单 Topic
     */
    public static final String ORDER_TOPIC = "TPP_ORDER_TOPIC";

    /**
     * 消费者组
     */
    public static final String PAYMENT_CONSUMER_GROUP = "TPP_PAYMENT_CONSUMER_GROUP";
    public static final String ORDER_CONSUMER_GROUP = "TPP_ORDER_CONSUMER_GROUP";

    /**
     * Tag
     */
    public static final String TAG_CANCEL_ORDER = "CANCEL_ORDER";
    public static final String TAG_ORDER_PAID = "ORDER_PAID";
    public static final String TAG_TIMEOUT_CHECK = "TIMEOUT_CHECK";
    public static final String TAG_LOCK_ACCEPTED = "LOCK_ACCEPTED";
    public static final String TAG_ORDER_CREATED_INTERNAL = "ORDER_CREATED_INTERNAL";

    private MqTopic() {
    }
}

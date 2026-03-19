package com.duanyan.taopiaopiao.orderservice.application.config;

public class OrderIdGenerator {
    private static final long START_TIMESTAMP = 1735660800000L;
    // 各部分占用的位数
    private static final long SEQUENCE_BIT = 12;      // 序列号
    private static final long TIMESTAMP_LEFT = SEQUENCE_BIT;
    // 毫秒内序列号
    private long sequence = 0L;

    // 每一部分的最大值
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);         // 4095
    // 上一次生成ID的时间戳
    private long lastTimestamp = -1L;
    public synchronized long nextId() {
        long currentTimestamp = getCurrentTimestamp();

        // 时钟回拨
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id.");
        }

        // 同一毫秒内
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;

            // 当前毫秒序列号用完，等下一毫秒
            if (sequence == 0L) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，从0开始
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_LEFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }

    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

}

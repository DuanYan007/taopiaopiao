package com.duanyan.taopiaopiao.seckillservice.application.config;

public class LockOrderNoGenerator {

    private static final long START_TIMESTAMP = 1735660800000L;
    private static final long SEQUENCE_BIT = 12;
    private static final long TIMESTAMP_LEFT = SEQUENCE_BIT;
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public synchronized long nextId() {
        long currentTimestamp = getCurrentTimestamp();
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id.");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;
        return ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_LEFT) | sequence;
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

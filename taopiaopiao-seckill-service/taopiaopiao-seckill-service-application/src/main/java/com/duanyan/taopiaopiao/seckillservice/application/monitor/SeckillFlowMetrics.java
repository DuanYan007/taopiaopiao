package com.duanyan.taopiaopiao.seckillservice.application.monitor;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

@Component
public class SeckillFlowMetrics {

    private final LongAdder bridgeSentTotal = new LongAdder();
    private final LongAdder bridgeFailedTotal = new LongAdder();
    private final LongAdder recoveryRetryTotal = new LongAdder();
    private final LongAdder recoveryTimeoutTotal = new LongAdder();
    private final LongAdder recoveryOrderCreatedCatchupTotal = new LongAdder();

    public void recordBridgeSent() {
        bridgeSentTotal.increment();
    }

    public void recordBridgeFailed() {
        bridgeFailedTotal.increment();
    }

    public void recordRecoveryRetry() {
        recoveryRetryTotal.increment();
    }

    public void recordRecoveryTimeout() {
        recoveryTimeoutTotal.increment();
    }

    public void recordRecoveryOrderCreatedCatchup() {
        recoveryOrderCreatedCatchupTotal.increment();
    }

    public long getBridgeSentTotal() {
        return bridgeSentTotal.sum();
    }

    public long getBridgeFailedTotal() {
        return bridgeFailedTotal.sum();
    }

    public long getRecoveryRetryTotal() {
        return recoveryRetryTotal.sum();
    }

    public long getRecoveryTimeoutTotal() {
        return recoveryTimeoutTotal.sum();
    }

    public long getRecoveryOrderCreatedCatchupTotal() {
        return recoveryOrderCreatedCatchupTotal.sum();
    }
}

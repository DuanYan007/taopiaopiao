package com.duanyan.taopiaopiao.orderservice.application.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RuntimeTestHookSupport {

    private final boolean enabled;
    private final Map<String, Long> timeoutCheckDelayMsByOrderNo = new ConcurrentHashMap<>();

    public RuntimeTestHookSupport(Environment environment) {
        this.enabled = environment.getProperty("tpp.test.runtime-hooks-enabled", Boolean.class, false);
    }

    public void armTimeoutCheckDelay(String orderNo, long delayMs) {
        ensureEnabled();
        if (delayMs <= 0) {
            timeoutCheckDelayMsByOrderNo.remove(orderNo);
            return;
        }
        timeoutCheckDelayMsByOrderNo.put(orderNo, delayMs);
        log.warn("已设置 TIMEOUT_CHECK 延迟钩子: orderNo={}, delayMs={}", orderNo, delayMs);
    }

    public void delayTimeoutCheckIfArmed(String orderNo) {
        if (!enabled) {
            return;
        }
        Long delayMs = timeoutCheckDelayMsByOrderNo.remove(orderNo);
        if (delayMs == null || delayMs <= 0) {
            return;
        }

        log.warn("命中 TIMEOUT_CHECK 延迟钩子: orderNo={}, delayMs={}", orderNo, delayMs);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TIMEOUT_CHECK 延迟钩子被中断: " + orderNo, e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Runtime test hooks are disabled");
        }
    }
}

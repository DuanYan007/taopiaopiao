package com.duanyan.taopiaopiao.seckillservice.application.support;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TestFailPointSupport {

    private final Environment environment;
    private final Set<String> activePoints = ConcurrentHashMap.newKeySet();

    public TestFailPointSupport(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        String configured = environment.getProperty("tpp.test.fail-points");
        if (!StringUtils.hasText(configured)) {
            configured = environment.getProperty("TPP_TEST_FAIL_POINTS");
        }
        if (!StringUtils.hasText(configured)) {
            return;
        }

        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(activePoints::add);
        log.warn("已启用测试故障注入点: {}", activePoints);
    }

    public void failOnce(String point) {
        if (activePoints.remove(point)) {
            log.warn("命中测试故障注入点: {}", point);
            throw new IllegalStateException("Injected fail point: " + point);
        }
    }
}

package com.duanyan.taopiaopiao.seckillservice.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeckillOrderConfig {

    @Bean
    public LockOrderNoGenerator lockOrderNoGenerator() {
        return new LockOrderNoGenerator();
    }
}

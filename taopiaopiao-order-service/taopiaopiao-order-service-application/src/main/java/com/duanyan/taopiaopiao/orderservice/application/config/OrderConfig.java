package com.duanyan.taopiaopiao.orderservice.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderConfig {
    @Bean
    public OrderIdGenerator orderIdGenerator(){
        return new OrderIdGenerator();
    }
}

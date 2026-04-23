package com.duanyan.taopiaopiao.seckillservice.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.duanyan.taopiaopiao.seckillservice.application",
        "com.duanyan.taopiaopiao.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.duanyan.taopiaopiao.seckillservice.application.client")
@EnableScheduling
public class SeckillServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillServiceApplication.class, args);
    }
}

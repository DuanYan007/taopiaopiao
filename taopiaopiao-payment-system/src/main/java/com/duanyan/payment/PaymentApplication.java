package com.duanyan.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 支付系统启动类
 *
 * @author duanyan
 * @since 1.0.0
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class
})
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}

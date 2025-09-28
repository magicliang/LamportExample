package com.example.dts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 分布式时间戳系统主应用类
 * 
 * @author DTS Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class DtsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DtsApplication.class, args);
    }
}
package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 用户服务启动类。
 * <p>
 * 基础包 org.example 会扫描到本模块的 controller/service/... 以及 common 模块中
 * org.example.exception 下的 GlobalExceptionHandler，无需额外配置。
 */
@EnableDiscoveryClient
@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

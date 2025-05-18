package com.costwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CostwiseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CostwiseApplication.class, args);
    }
} 
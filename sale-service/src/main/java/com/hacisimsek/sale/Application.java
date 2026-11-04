package com.hacisimsek.sale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.hacisimsek")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
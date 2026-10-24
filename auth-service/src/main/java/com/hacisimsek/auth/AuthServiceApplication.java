package com.hacisimsek.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@EnableScheduling
public class AuthServiceApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
    @PostConstruct
    public void testEnv() {
        System.out.println("Google ID = " + System.getenv("GOOGLE_CLIENT_ID"));
        System.out.println("Mail User = " + System.getenv("MAIL_USERNAME"));
    }
}

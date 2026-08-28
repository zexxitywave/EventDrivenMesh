package com.hacisimsek.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = {"com.hacisimsek.shipping", "com.hacisimsek.common"})
@EnableDiscoveryClient
@EnableKafka
public class ShippingServiceApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }
}

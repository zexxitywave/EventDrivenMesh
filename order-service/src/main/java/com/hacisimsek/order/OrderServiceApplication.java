package com.hacisimsek.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.TimeZone;


@SpringBootApplication(scanBasePackages = {"com.hacisimsek.order", "com.hacisimsek.common"})
@EnableDiscoveryClient
@EnableKafka
public class OrderServiceApplication {

	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}

package com.hacisimsek.cart.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * @LoadBalanced makes RestTemplate resolve service names from Eureka.
     * e.g. http://product-service/api/products/... resolves via Eureka discovery.
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Plain RestTemplate for a fixed URL (the coupon-service is called by
     * host:port, not by Eureka service name, so it works even when the coupon
     * service is not registered).
     */
    @Bean
    public RestTemplate plainRestTemplate() {
        return new RestTemplate();
    }
}
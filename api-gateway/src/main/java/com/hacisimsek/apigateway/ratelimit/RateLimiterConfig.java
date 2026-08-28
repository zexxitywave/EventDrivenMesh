package com.hacisimsek.apigateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Defines named RedisRateLimiter beans for each sensitivity tier.
 *
 * Token Bucket algorithm (built into Spring Cloud Gateway + Redis):
 *
 *   replenishRate  — tokens added to the bucket every second (sustained req/s)
 *   burstCapacity  — max tokens the bucket can hold (peak spike allowed)
 *   requestedTokens — tokens consumed per request (always 1 here)
 *
 * Rule of thumb: burstCapacity = replenishRate * 2  (allow 2s worth of burst)
 *
 * ┌─────────────────┬───────────────┬───────────────┬──────────────────────────────────┐
 * │ Bean name       │ replenishRate │ burstCapacity │ Used for                         │
 * ├─────────────────┼───────────────┼───────────────┼──────────────────────────────────┤
 * │ authRateLimiter │      5        │      10       │ /api/auth/login, /register        │
 * │ strictLimiter   │      5        │      10       │ /api/payments/** (sensitive)      │
 * │ standardLimiter │     20        │      40       │ orders, cart, users, seller       │
 * │ relaxedLimiter  │     50        │     100       │ products (high read traffic)      │
 * │ webhookLimiter  │    100        │     200       │ /api/payments/webhook (Razorpay)  │
 * └─────────────────┴───────────────┴───────────────┴──────────────────────────────────┘
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Auth endpoints — tightest limit to prevent brute-force and account spam.
     * 5 req/s sustained, burst up to 10.
     */
    @Bean
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(5, 10, 1);
    }

    /**
     * Strict — payment initiation, order creation.
     * Sensitive operations that should never be hammered.
     * 5 req/s sustained, burst up to 10.
     */
    @Bean
    public RedisRateLimiter strictLimiter() {
        return new RedisRateLimiter(5, 10, 1);
    }
    /**
     * Standard — most authenticated services (cart, user, wishlist, shipping,
     * notifications, seller, inventory, logging).
     * 20 req/s sustained, burst up to 40.
     * Marked @Primary so Spring Cloud Gateway auto-config resolves this as default.
     */
    @Bean
    @Primary
    public RedisRateLimiter standardLimiter() {
        return new RedisRateLimiter(20, 40, 1);
    }
    /**
     * Relaxed — product catalog reads.
     * High read traffic is expected and acceptable.
     * 50 req/s sustained, burst up to 100.
     */
    @Bean
    public RedisRateLimiter relaxedLimiter() {
        return new RedisRateLimiter(50, 100, 1);
    }

    /**
     * Webhook — Razorpay pushes events here directly.
     * Must never be blocked: Razorpay retries on 5xx but gives up on sustained 429s.
     * 100 req/s sustained, burst up to 200.
     */
    @Bean
    public RedisRateLimiter webhookLimiter() {
        return new RedisRateLimiter(100, 200, 1);
    }
}

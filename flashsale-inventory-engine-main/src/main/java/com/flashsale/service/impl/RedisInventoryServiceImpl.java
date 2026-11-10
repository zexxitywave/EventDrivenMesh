package com.flashsale.service.impl;

import com.flashsale.service.RedisInventoryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;

@Service
public class RedisInventoryServiceImpl implements RedisInventoryService {

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<Long> reserveScript;

    public RedisInventoryServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.reserveScript = new DefaultRedisScript<>();

        this.reserveScript.setScriptText(
                """
                local stock = tonumber(redis.call('GET', KEYS[1]))
    
                if stock == nil then
                    return -1
                end
    
                if stock < tonumber(ARGV[1]) then
                    return 0
                end
    
                redis.call('DECRBY', KEYS[1], ARGV[1])
    
                return 1
                """
        );

        this.reserveScript.setResultType(Long.class);
    }

    @Override
    public boolean reserveStock(Long productId, int quantity) {

        String key = "inventory:stock:" + productId;

        Long result = redisTemplate.execute(
                reserveScript,
                Collections.singletonList(key),
                String.valueOf(quantity)
        );

        return result != null && result == 1;
    }

    @Override
    public Long getStock(Long productId) {

        String key = "inventory:stock:" + productId;

        String value = redisTemplate.opsForValue().get(key);

        return value == null ? null : Long.parseLong(value);
    }
    @Override
    public void initializeStock(Long productId, int quantity) {

        String key = "inventory:stock:" + productId;

        redisTemplate.opsForValue()
                .set(key, String.valueOf(quantity));
    }
    @Override
    public void releaseStock(Long productId, int quantity) {

        String key = "inventory:stock:" + productId;

        redisTemplate.opsForValue()
                .increment(key, quantity);
    }
}
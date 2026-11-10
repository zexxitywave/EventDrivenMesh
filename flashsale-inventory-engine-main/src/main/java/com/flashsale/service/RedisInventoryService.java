package com.flashsale.service;

public interface RedisInventoryService {

    void initializeStock(Long productId, int quantity);

    boolean reserveStock(Long productId, int quantity);

    void releaseStock(Long productId, int quantity);

    Long getStock(Long productId);
}

package com.flashsale.service;

import com.flashsale.entity.Cart;
import com.flashsale.entity.CartItem;

import java.util.List;

public interface CartService {

    Cart getCartByUserId(Long userId);

    CartItem addItem(Long userId, Long productId, Integer quantity);

    void removeItem(Long userId, Long cartItemId);

    List<CartItem> getCartItems(Long userId);

    void clearCart(Long userId);
}

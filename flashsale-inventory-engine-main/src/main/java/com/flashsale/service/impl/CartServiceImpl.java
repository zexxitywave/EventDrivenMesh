package com.flashsale.service.impl;

import com.flashsale.entity.Cart;
import com.flashsale.entity.CartItem;
import com.flashsale.entity.Product;
import com.flashsale.repository.CartItemRepository;
import com.flashsale.repository.CartRepository;
import com.flashsale.repository.ProductRepository;
import com.flashsale.repository.UserRepository;
import com.flashsale.service.CartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Cart getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart cart = Cart.builder()
                            .user(userRepository.findById(userId)
                                    .orElseThrow(() -> new RuntimeException("User not found")))
                            .build();
                    return cartRepository.save(cart);
                });
    }

    @Override
    @Transactional
    public CartItem addItem(Long userId, Long productId, Integer quantity) {
        Cart cart = getCartByUserId(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // If item already exists, increment quantity
        return cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .map(existing -> {
                    existing.setQuantity(existing.getQuantity() + quantity);
                    return cartItemRepository.save(existing);
                })
                .orElseGet(() -> {
                    CartItem item = CartItem.builder()
                            .cart(cart)
                            .product(product)
                            .quantity(quantity)
                            .build();
                    return cartItemRepository.save(item);
                });
    }

    @Override
    @Transactional
    public void removeItem(Long userId, Long cartItemId) {
        Cart cart = getCartByUserId(userId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("Cart item does not belong to user's cart");
        }
        cartItemRepository.delete(item);
    }

    @Override
    public List<CartItem> getCartItems(Long userId) {
        Cart cart = getCartByUserId(userId);
        return cartItemRepository.findByCartId(cart.getId());
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getCartByUserId(userId);
        cartItemRepository.deleteByCartId(cart.getId());
    }
}

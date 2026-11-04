package com.hacisimsek.cart.controller;

import com.hacisimsek.cart.dto.AbandonedCartResponse;
import com.hacisimsek.cart.dto.AbandonedCartsResponse;
import com.hacisimsek.cart.dto.ReminderStateRequest;
import com.hacisimsek.cart.model.Cart;
import com.hacisimsek.cart.model.ReminderStage;
import com.hacisimsek.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Internal endpoints used by automation (n8n), not by the storefront.
 *
 * <p>These are deliberately separate from {@link CartController}: the public
 * cart API is scoped to one user via {@code X-User-Id}, while these endpoints
 * look across carts and advance workflow state.</p>
 *
 * <p><strong>Security:</strong> the API Gateway does not route
 * {@code /internal/**}. In production, also restrict these endpoints to your
 * automation network or require a shared secret header.</p>
 */
@RestController
@RequestMapping("/api/v1/cart/internal")
@RequiredArgsConstructor
public class InternalCartController {

    private final CartService cartService;

    /**
     * Find carts with items that have not changed for at least
     * {@code olderThanMinutes}. Defaults to carts whose reminder stage is
     * {@code NONE} when {@code stage} is provided by the caller.
     *
     * <p>Example: {@code GET /api/v1/cart/internal/abandoned?olderThanMinutes=30&stage=NONE&limit=100}</p>
     */
    @GetMapping("/abandoned")
    public ResponseEntity<AbandonedCartsResponse> getAbandonedCarts(
            @RequestParam(defaultValue = "30") int olderThanMinutes,
            @RequestParam(required = false) ReminderStage stage,
            @RequestParam(defaultValue = "100") int limit) {
        List<AbandonedCartResponse> carts =
                cartService.getAbandonedCarts(olderThanMinutes, stage, limit);
        return ResponseEntity.ok(AbandonedCartsResponse.builder()
                .count(carts.size())
                .carts(carts)
                .build());
    }

    /**
     * Advance the recovery state for a cart so the next scheduled run does not
     * send the same email again.
     */
    @PatchMapping("/{userId}/reminder-state")
    public ResponseEntity<Cart> updateReminderState(
            @PathVariable UUID userId,
            @Valid @RequestBody ReminderStateRequest request) {
        return ResponseEntity.ok(
                cartService.markReminderStage(userId, request.getStage()));
    }

    /**
     * TEST ONLY — backdates the cart and resets its reminder stage so you can
     * trigger the n8n workflow without waiting for the abandonment window.
     */
    @PostMapping("/{userId}/simulate-idle")
    public ResponseEntity<Cart> simulateIdle(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "45") long minutes) {
        return ResponseEntity.ok(cartService.simulateIdle(userId, minutes));
    }
}

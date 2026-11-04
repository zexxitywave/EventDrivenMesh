package com.hacisimsek.cart.model;

/**
 * Tracks how far the abandoned-cart recovery workflow has progressed for a cart.
 *
 * <p>The workflow itself lives in n8n (see {@code n8n/README.md}). The cart owns
 * this small piece of state so that a scheduled run never reminds the same
 * customer twice. Any new cart activity resets the stage back to {@link #NONE}.</p>
 */
public enum ReminderStage {
    /** No reminder has been sent for the current abandoned period. */
    NONE,
    /** The first "you left something behind" email has been sent. */
    REMINDED,
    /** The 24h follow-up email with a coupon has been sent. */
    COUPON_SENT
}

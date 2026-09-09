package com.hacisimsek.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when /verify is attempted without a gateway order issued by
 * /initiate, or when the supplied gatewayOrderId does not match the one
 * minted during initiation. Stops payments from being completed against
 * an order that was never initiated through the backend.
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PaymentNotInitiatedException extends RuntimeException {

    public PaymentNotInitiatedException(String message) {
        super(message);
    }
}
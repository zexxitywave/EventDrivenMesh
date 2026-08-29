package com.hacisimsek.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a payment initiation is attempted for an order
 * that already has a PENDING or COMPLETED payment.
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class PaymentAlreadyExistsException extends RuntimeException {

    public PaymentAlreadyExistsException(String message) {
        super(message);
    }
}

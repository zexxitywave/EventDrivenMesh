package com.flashsale.service;

import com.flashsale.entity.Payment;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentService {

    Payment createPayment(Long orderId, BigDecimal amount, String transactionReference);

    Payment confirmPayment(Long paymentId);

    Payment refundPayment(Long paymentId);

    Payment getPaymentById(Long paymentId);

    List<Payment> getPaymentsByOrderId(Long orderId);
}

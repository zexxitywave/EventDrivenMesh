package com.hacisimsek.shipping.service;

import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.shipping.dto.DeliveryZoneSummary;
import com.hacisimsek.shipping.model.Shipment;

import java.util.List;
import java.util.UUID;

public interface ShippingService {
    void processShipping(PaymentProcessedEvent paymentEvent);
    Shipment getShipmentByOrderId(UUID orderId);
    Shipment getShipmentById(UUID shipmentId);
    List<DeliveryZoneSummary> getZoneSummaries();
}
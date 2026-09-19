package com.hacisimsek.order.client;

import com.hacisimsek.common.dto.ShippingQuoteRequest;
import com.hacisimsek.common.dto.ShippingQuoteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Calls shipping-service's quote endpoint to price delivery BEFORE the order
 * reaches payment, so the customer pays items + delivery in one amount.
 * Never throws — falls back to a configured default charge so a geocoding or
 * network outage cannot block order creation.
 */
@Component
@Slf4j
public class ShippingQuoteClient {

    private final RestTemplate restTemplate;
    private final String quoteUrl;
    private final BigDecimal fallbackDeliveryCharge;

    public ShippingQuoteClient(RestTemplate restTemplate,
                               @Value("${order.shipping.quote-url:http://shipping-service/api/v1/shipping/quotes}") String quoteUrl,
                               @Value("${order.shipping.fallback-delivery-charge:99.00}") BigDecimal fallbackDeliveryCharge) {
        this.restTemplate = restTemplate;
        this.quoteUrl = quoteUrl;
        this.fallbackDeliveryCharge = fallbackDeliveryCharge;
    }

    public BigDecimal getDeliveryCharge(String address) {
        try {
            ShippingQuoteResponse response = restTemplate.postForObject(
                    quoteUrl, new ShippingQuoteRequest(address), ShippingQuoteResponse.class);
            if (response != null && response.deliveryCharge() != null) {
                return response.deliveryCharge();
            }
            log.warn("Shipping quote returned no charge for '{}' — using fallback {}", address, fallbackDeliveryCharge);
            return fallbackDeliveryCharge;
        } catch (Exception e) {
            log.warn("Shipping quote failed for '{}' — using fallback {}: {}",
                    address, fallbackDeliveryCharge, e.getMessage());
            return fallbackDeliveryCharge;
        }
    }
}
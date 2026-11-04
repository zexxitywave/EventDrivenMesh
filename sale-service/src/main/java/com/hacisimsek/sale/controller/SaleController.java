package com.hacisimsek.sale.controller;

import com.hacisimsek.common.event.sale.SaleStartedEvent;
import com.hacisimsek.common.event.BaseEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final KafkaTemplate<String, BaseEvent> kafkaTemplate;

    public SaleController(KafkaTemplate<String, BaseEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/start")
    public void start(@RequestBody SaleStartReq req) {
        SaleStartedEvent event = new SaleStartedEvent(
                req.campaignId,
                req.startAt,
                req.discountPercent,
                req.userIds
        );

        String topic = "sale-started";
        kafkaTemplate.send(topic, event);
    }

    public static class SaleStartReq {
        public String campaignId;
        public Instant startAt;
        public int discountPercent;
        public List<String> userIds;
    }
}
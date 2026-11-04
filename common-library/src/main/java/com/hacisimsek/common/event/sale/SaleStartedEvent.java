package com.hacisimsek.common.event.sale;

import com.hacisimsek.common.event.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.Instant;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class SaleStartedEvent extends BaseEvent {

    private final String campaignId;
    private final Instant startAt;
    private final int discountPercent;
    private final List<String> userIds;

    public SaleStartedEvent(String campaignId, Instant startAt, int discountPercent, List<String> userIds) {
        this.campaignId = campaignId;
        this.startAt = startAt;
        this.discountPercent = discountPercent;
        this.userIds = userIds;
    }
}
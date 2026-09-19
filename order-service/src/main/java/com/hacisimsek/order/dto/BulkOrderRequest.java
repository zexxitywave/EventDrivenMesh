package com.hacisimsek.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkOrderRequest {
    @NotEmpty
    @Size(min = 1, max = 10000, message = "Batch must contain between 1 and 10000 orders")
    @Valid
    private List<OrderRequest> orders;

    @Min(value = 1, message = "count must be at least 1")
    @Max(value = 10000, message = "count must not exceed 10000")
    private Integer count;
}
package com.hacisimsek.order.dto;

import jakarta.validation.Valid;
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
    @Size(min = 1, max = 200, message = "Batch must contain between 1 and 200 orders")
    @Valid
    private List<OrderRequest> orders;
}
package com.hacisimsek.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Minimal contact projection used by automation (n8n) to resolve the email
 * address for a cart owner. Avoids exposing the full profile/address graph.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerContactResponse {

    private UUID userId;
    private String fullName;
    private String email;
    private boolean emailNotifications;
}

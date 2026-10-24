package com.hacisimsek.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded value object — stored in user_profiles table, not a separate table.
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {

    @Column(name = "pref_language")
    @Builder.Default
    private String language = "en";

    @Column(name = "pref_currency")
    @Builder.Default
    private String currency = "USD";

    /** Whether the user wants email notifications. */
    @Column(name = "pref_email_notifications")
    @Builder.Default
    private boolean emailNotifications = true;

    /** Whether the user wants SMS notifications. */
    @Column(name = "pref_sms_notifications")
    @Builder.Default
    private boolean smsNotifications = false;

    /** Whether the user wants push notifications. */
    @Column(name = "pref_push_notifications")
    @Builder.Default
    private boolean pushNotifications = true;
}

package com.hacisimsek.user.dto;

import lombok.Data;

@Data
public class PreferencesRequest {
    private String language;
    private String currency;
    private Boolean emailNotifications;
    private Boolean smsNotifications;
    private Boolean pushNotifications;
}

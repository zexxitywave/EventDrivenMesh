package com.hacisimsek.user.controller;

import com.hacisimsek.user.dto.AddressRequest;
import com.hacisimsek.user.dto.CreateProfileRequest;
import com.hacisimsek.user.dto.PreferencesRequest;
import com.hacisimsek.user.dto.UpdateProfileRequest;
import com.hacisimsek.user.model.Address;
import com.hacisimsek.user.model.UserProfile;
import com.hacisimsek.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    // â”€â”€ Profile Endpoints â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Create profile â€” called after registration.
     * The userId comes from the X-User-Id header injected by the API Gateway JWT filter.
     */
    @PostMapping("/profile")
    public ResponseEntity<UserProfile> createProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.createProfile(userId, request));
    }
//
    /**
     * Get my profile.
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfile> getMyProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }

    /**
     * Update my profile (name, phone).
     */
    @PutMapping("/profile")
    public ResponseEntity<UserProfile> updateProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
    }

    /**
     * Deactivate account.
     */
    @DeleteMapping("/profile")
    public ResponseEntity<Void> deactivateProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        userProfileService.deactivateProfile(userId);
        return ResponseEntity.noContent().build();
    }

    // â”€â”€ Preferences Endpoints â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Update preferences (language, currency, notification settings).
     */
    @PatchMapping("/profile/preferences")
    public ResponseEntity<UserProfile> updatePreferences(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody PreferencesRequest request) {
        return ResponseEntity.ok(userProfileService.updatePreferences(userId, request));
    }

    // â”€â”€ Address Endpoints â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Get all addresses for the logged-in user.
     */
    @GetMapping("/addresses")
    public ResponseEntity<List<Address>> getAddresses(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(userProfileService.getAddresses(userId));
    }

    /**
     * Add a new shipping address.
     */
    @PostMapping("/addresses")
    public ResponseEntity<Address> addAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.addAddress(userId, request));
    }

    /**
     * Update an existing address.
     */
    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<Address> updateAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(userProfileService.updateAddress(userId, addressId, request));
    }

    /**
     * Delete an address.
     */
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID addressId) {
        userProfileService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Set a specific address as the default shipping address.
     */
    @PatchMapping("/addresses/{addressId}/default")
    public ResponseEntity<Address> setDefaultAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID addressId) {
        return ResponseEntity.ok(userProfileService.setDefaultAddress(userId, addressId));
    }
}

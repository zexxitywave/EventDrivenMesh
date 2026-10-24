package com.hacisimsek.user.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores user profile/business data.
 * Authentication is handled by auth-service.
 * User is identified by the UUID injected by the API Gateway (X-User-Id header).
 */
@Entity
@Table(name = "user_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    /** Same UUID as the user in auth-service — no separate auto-generation. */
    @Id
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus;

    /** Embedded user preferences (language, currency, notifications). */
    @Embedded
    private UserPreferences preferences;

    /** List of shipping addresses owned by this user. */
    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<Address> addresses = new ArrayList<>();

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.accountStatus == null) this.accountStatus = AccountStatus.ACTIVE;
        if (this.preferences == null)   this.preferences = new UserPreferences();
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

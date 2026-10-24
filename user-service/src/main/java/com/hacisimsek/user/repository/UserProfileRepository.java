package com.hacisimsek.user.repository;

import com.hacisimsek.user.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByEmail(String email);
    boolean existsByEmail(String email);
}

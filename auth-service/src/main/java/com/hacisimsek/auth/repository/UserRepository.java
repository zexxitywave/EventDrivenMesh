package com.hacisimsek.auth.repository;

import com.hacisimsek.auth.model.AuthProvider;
import com.hacisimsek.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
    boolean existsByEmail(String email);
}

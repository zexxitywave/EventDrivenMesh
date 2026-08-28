package com.hacisimsek.seller.repository;

import com.hacisimsek.seller.model.SellerProfile;
import com.hacisimsek.seller.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerRepository extends JpaRepository<SellerProfile, UUID> {

    Optional<SellerProfile> findByUserId(UUID userId);

    Optional<SellerProfile> findByEmail(String email);

    boolean existsByUserId(UUID userId);

    boolean existsByEmail(String email);

    List<SellerProfile> findByVerificationStatus(VerificationStatus status);

    /** Sellers eligible for the product approval queue — only VERIFIED sellers. */
    @Query("SELECT s FROM SellerProfile s WHERE s.verificationStatus = 'VERIFIED' ORDER BY s.rating DESC")
    List<SellerProfile> findVerifiedSellers();
}

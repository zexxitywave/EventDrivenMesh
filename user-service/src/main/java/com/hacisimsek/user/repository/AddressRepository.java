package com.hacisimsek.user.repository;

import com.hacisimsek.user.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserProfileId(UUID userProfileId);

    Optional<Address> findByIdAndUserProfileId(UUID id, UUID userProfileId);

    /** Clears default flag on all addresses for a user before setting a new default. */
    @Modifying
    @Query("UPDATE Address a SET a.defaultAddress = false WHERE a.userProfile.id = :userId")
    void clearDefaultForUser(UUID userId);
}

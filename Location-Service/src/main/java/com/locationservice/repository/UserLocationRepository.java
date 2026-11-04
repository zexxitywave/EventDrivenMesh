package com.locationservice.repository;

import com.locationservice.entity.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {

    /** Most-recent captures first. */
    List<UserLocation> findAllByOrderByCreatedAtDesc();
}

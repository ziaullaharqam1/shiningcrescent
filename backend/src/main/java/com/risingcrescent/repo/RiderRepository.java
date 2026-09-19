package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.Rider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiderRepository extends JpaRepository<Rider, Long> {
    List<Rider> findByAvailableTrueAndOnJobFalse();
}

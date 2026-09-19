package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScreenRepository extends JpaRepository<Screen, Long> {
    Optional<Screen> findByCode(String code);
    boolean existsByCode(String code);
}

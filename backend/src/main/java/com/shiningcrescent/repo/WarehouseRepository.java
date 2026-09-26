package com.shiningcrescent.repo;

import com.shiningcrescent.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByCode(String code);
}

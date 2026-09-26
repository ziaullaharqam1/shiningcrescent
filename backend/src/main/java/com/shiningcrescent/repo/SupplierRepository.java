package com.shiningcrescent.repo;

import com.shiningcrescent.domain.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}

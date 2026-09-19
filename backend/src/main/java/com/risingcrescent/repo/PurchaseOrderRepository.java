package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.PurchaseOrder;
import com.risingcrescent.domain.enums.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStatus(PurchaseStatus status);
    List<PurchaseOrder> findBySupplier_PortalUser_Username(String username);
    Optional<PurchaseOrder> findByPoNo(String poNo);
}

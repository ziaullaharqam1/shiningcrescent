package com.shiningcrescent.repo;

import com.shiningcrescent.domain.entity.InventoryLot;
import com.shiningcrescent.domain.enums.LotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {
    List<InventoryLot> findByProduct_IdAndStatusOrderByExpiryOnAsc(Long productId, LotStatus status);
    List<InventoryLot> findByProduct_Id(Long productId);
    List<InventoryLot> findBySupplier_PortalUser_Username(String username);
}

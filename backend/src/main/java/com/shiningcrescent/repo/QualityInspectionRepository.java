package com.shiningcrescent.repo;

import com.shiningcrescent.domain.entity.QualityInspection;
import com.shiningcrescent.domain.enums.InspectionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QualityInspectionRepository extends JpaRepository<QualityInspection, Long> {
    List<QualityInspection> findByResult(InspectionResult result);
    java.util.Optional<QualityInspection> findByInspectionNo(String inspectionNo);
    List<QualityInspection> findByLot_Id(Long lotId);
    List<QualityInspection> findByPurchaseOrder_Id(Long purchaseOrderId);
}

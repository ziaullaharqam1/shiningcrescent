package com.shiningcrescent.repo;

import com.shiningcrescent.domain.entity.Delivery;
import com.shiningcrescent.domain.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrder_Id(Long orderId);
    List<Delivery> findByStatusIn(List<DeliveryStatus> statuses);
}

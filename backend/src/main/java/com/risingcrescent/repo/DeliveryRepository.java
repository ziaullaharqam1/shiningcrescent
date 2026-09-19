package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.Delivery;
import com.risingcrescent.domain.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrder_Id(Long orderId);
    List<Delivery> findByStatusIn(List<DeliveryStatus> statuses);
}

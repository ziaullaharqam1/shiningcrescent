package com.shiningcrescent.repo;

import com.shiningcrescent.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByReference(String reference);
    java.util.List<Payment> findByOrder_Id(Long orderId);
}

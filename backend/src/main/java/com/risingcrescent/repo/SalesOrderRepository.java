package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.SalesOrder;
import com.risingcrescent.domain.entity.UserAccount;
import com.risingcrescent.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByCustomerOrderByPlacedAtDesc(UserAccount customer);
    List<SalesOrder> findByStatus(OrderStatus status);
    Optional<SalesOrder> findByOrderNo(String orderNo);
    Optional<SalesOrder> findByStripePaymentIntentId(String stripePaymentIntentId);
    long countByStatus(OrderStatus status);
}

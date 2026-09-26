package com.shiningcrescent.repo;

import com.shiningcrescent.domain.entity.SalesOrder;
import com.shiningcrescent.domain.entity.UserAccount;
import com.shiningcrescent.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByCustomerOrderByPlacedAtDesc(UserAccount customer);
    List<SalesOrder> findByStatus(OrderStatus status);
    Optional<SalesOrder> findByOrderNo(String orderNo);
    Optional<SalesOrder> findByStripePaymentIntentId(String stripePaymentIntentId);
    long countByStatus(OrderStatus status);

    @EntityGraph(attributePaths = {"lines", "lines.product", "customer"})
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findWithLinesById(@Param("id") Long id);
}

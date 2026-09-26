package com.shiningcrescent.domain.entity;

import com.shiningcrescent.domain.enums.Channel;
import com.shiningcrescent.domain.enums.OrderStatus;
import com.shiningcrescent.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNo;

    @ManyToOne(optional = false)
    private UserAccount customer;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String shipToName;
    private String shipToPhone;
    private String shipToAddress;
    private String notes;

    @Builder.Default
    private boolean leaveAtDoor = false;

    private Double dropLat;
    private Double dropLng;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    private String stripePaymentIntentId;
    private String stripeTipIntentId;
    private String paymentMethod;

    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalesOrderLine> lines = new ArrayList<>();

    private Instant placedAt;
    private Instant updatedAt;
    private String lastActor;

    @PrePersist
    void onCreate() {
        placedAt = Instant.now();
        updatedAt = placedAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

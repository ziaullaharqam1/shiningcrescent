package com.shiningcrescent.domain.entity;

import com.shiningcrescent.domain.enums.PurchaseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String poNo;

    @ManyToOne(optional = false)
    private Supplier supplier;

    @ManyToOne
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    private PurchaseStatus status;

    private LocalDate expectedDate;
    private String notes;
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    private Instant createdAt;
    private String createdBy;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

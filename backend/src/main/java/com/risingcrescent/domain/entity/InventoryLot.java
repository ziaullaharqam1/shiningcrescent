package com.risingcrescent.domain.entity;

import com.risingcrescent.domain.enums.LotStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_lots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryLot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String lotCode;

    @ManyToOne(optional = false)
    private Product product;

    @ManyToOne(optional = false)
    private Warehouse warehouse;

    @ManyToOne
    private Supplier supplier;

    @Builder.Default
    private BigDecimal receivedQty = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal availableQty = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal reservedQty = BigDecimal.ZERO;

    private LocalDate receivedOn;
    private LocalDate expiryOn;
    private String grade;
    private BigDecimal moisturePct;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LotStatus status = LotStatus.QUARANTINE;
}

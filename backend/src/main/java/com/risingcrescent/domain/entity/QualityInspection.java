package com.risingcrescent.domain.entity;

import com.risingcrescent.domain.enums.InspectionResult;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "quality_inspections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualityInspection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String inspectionNo;

    @ManyToOne(optional = false)
    private InventoryLot lot;

    @ManyToOne
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    private InspectionResult result;

    private BigDecimal moisturePct;
    private String foreignMatter;
    private String appearance;
    private String remarks;
    private String inspector;
    private Instant inspectedAt;
}

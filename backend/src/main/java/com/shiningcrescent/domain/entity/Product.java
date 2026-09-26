package com.shiningcrescent.domain.entity;

import com.shiningcrescent.domain.enums.ProductKind;
import com.shiningcrescent.domain.enums.ProductStatus;
import com.shiningcrescent.domain.enums.UnitOfMeasure;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @ManyToOne(optional = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    private ProductKind kind;

    @Enumerated(EnumType.STRING)
    private UnitOfMeasure uom;

    private String origin;
    private String grade;
    private String season;
    private String variety;
    private Integer shelfLifeDays;
    private BigDecimal moistureMaxPct;
    private String storageHint;
    private String imageHint;
    private String imageUrl;
    private BigDecimal retailPrice;
    private BigDecimal wholesalePrice;
    private BigDecimal minWholesaleQty;
    @Builder.Default
    private boolean perishable = true;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

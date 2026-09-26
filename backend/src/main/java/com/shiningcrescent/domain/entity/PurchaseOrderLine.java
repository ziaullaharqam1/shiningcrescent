package com.shiningcrescent.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(optional = false)
    private Product product;

    private BigDecimal qty;
    private BigDecimal unitCost;
    @Builder.Default
    private BigDecimal receivedQty = BigDecimal.ZERO;
}

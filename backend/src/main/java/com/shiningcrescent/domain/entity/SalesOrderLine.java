package com.shiningcrescent.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sales_order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private SalesOrder order;

    @ManyToOne(optional = false)
    private Product product;

    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private String allocatedLot;
}

package com.risingcrescent.domain.entity;

import com.risingcrescent.domain.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNo;

    @OneToOne(optional = false)
    private SalesOrder order;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    private BigDecimal amount;
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private LocalDate dueDate;
    private Instant issuedAt;
}

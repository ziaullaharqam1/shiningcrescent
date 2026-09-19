package com.risingcrescent.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "riders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String vehicle;
    private String noonRiderCode;

    @Builder.Default
    private BigDecimal rating = new BigDecimal("4.80");
    @Builder.Default
    private int jobsCompleted = 0;
    @Builder.Default
    private boolean available = true;
    @Builder.Default
    private boolean onJob = false;

    private Double lastLat;
    private Double lastLng;
}

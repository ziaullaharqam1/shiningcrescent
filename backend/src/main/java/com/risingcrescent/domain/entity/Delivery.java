package com.risingcrescent.domain.entity;

import com.risingcrescent.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private SalesOrder order;

    @ManyToOne
    private Rider rider;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    private String provider;
    private String noonAwb;
    private String noonShipmentNr;
    private String noonJobId;

    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Double riderLat;
    private Double riderLng;

    /** SCOOTER or CAR — chosen from the order, shown on the live map. */
    @Builder.Default
    private String vehicleKind = "SCOOTER";

    private Integer etaMinutes;
    private Instant etaAt;
    private Instant assignedAt;
    private Instant departedAt;
    private Instant deliveredAt;

    @Builder.Default
    private BigDecimal tipAmount = BigDecimal.ZERO;
    private Integer customerRating;
    private String feedback;
}

package com.risingcrescent.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String contactName;
    private String email;
    private String phone;
    private String originCountry;
    private String specialties;
    @Builder.Default
    private boolean preferred = false;
    @Builder.Default
    private boolean active = true;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne
    private UserAccount portalUser;
}

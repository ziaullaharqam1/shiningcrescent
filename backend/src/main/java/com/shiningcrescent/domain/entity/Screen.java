package com.shiningcrescent.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "screens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Screen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    private String module;
    private String path;
    private int sortOrder;
}

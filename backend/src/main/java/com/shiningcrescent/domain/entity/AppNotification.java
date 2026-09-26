package com.shiningcrescent.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipientUsername;
    private String title;
    @Column(length = 2000)
    private String body;
    private String type;
    private String refType;
    private String refId;
    @Builder.Default
    private boolean readFlag = false;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

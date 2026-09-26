package com.shiningcrescent.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "portal_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalSettings {
    @Id
    private Long id;

    @Column(nullable = false)
    private String brandName;

    private String logoUrl;

    @Column(length = 16)
    private String primaryColor;

    @Column(length = 16)
    private String hoverColor;

    @Column(length = 16)
    private String softColor;

    @Column(length = 16)
    private String inkColor;

    /** Storefront / login footer hub locations line. */
    @Column(length = 500)
    private String hubLine;

    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    @Column(length = 500)
    private String smtpPassword;
    private String smtpFrom;
    @Builder.Default
    private boolean smtpAuth = true;
    @Builder.Default
    private boolean smtpStartTls = true;
}

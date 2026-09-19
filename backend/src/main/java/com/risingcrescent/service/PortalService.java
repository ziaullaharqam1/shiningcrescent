package com.risingcrescent.service;

import com.risingcrescent.audit.AuditService;
import com.risingcrescent.domain.entity.PortalSettings;
import com.risingcrescent.domain.entity.Product;
import com.risingcrescent.domain.entity.Screen;
import com.risingcrescent.repo.PortalSettingsRepository;
import com.risingcrescent.repo.ProductRepository;
import com.risingcrescent.repo.RoleRepository;
import com.risingcrescent.repo.ScreenRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PortalService {
    public static final long SETTINGS_ID = 1L;
    private static final Pattern HEX = Pattern.compile("^#([0-9A-Fa-f]{6})$");

    private final PortalSettingsRepository settings;
    private final ScreenRepository screens;
    private final RoleRepository roles;
    private final ProductRepository products;
    private final FileStorage files;
    private final AuditService audit;
    private final CatalogService catalog;

    @Transactional
    public void ensureDefaults() {
        if (screens.findByCode("PORTAL").isEmpty()) {
            screens.save(Screen.builder()
                    .code("PORTAL")
                    .name("Portal admin")
                    .module("Admin")
                    .path("/console/portal")
                    .sortOrder(155)
                    .build());
        }
        roles.findByCode("SUPER_ADMIN").ifPresent(role -> {
            boolean changed = role.getPermissions().add("PORTAL:VIEW")
                    | role.getPermissions().add("PORTAL:UPDATE");
            if (changed) {
                roles.save(role);
            }
        });
        if (!settings.existsById(SETTINGS_ID)) {
            settings.save(defaults());
        }
    }

    public Map<String, Object> publicBranding() {
        PortalSettings s = current();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brandName", s.getBrandName());
        m.put("logoUrl", s.getLogoUrl() == null ? "" : s.getLogoUrl());
        m.put("primaryColor", s.getPrimaryColor());
        m.put("hoverColor", s.getHoverColor());
        m.put("softColor", s.getSoftColor());
        m.put("inkColor", s.getInkColor());
        return m;
    }

    public Map<String, Object> adminView() {
        PortalSettings s = current();
        Map<String, Object> m = new LinkedHashMap<>(publicBranding());
        m.put("smtpHost", s.getSmtpHost() == null ? "" : s.getSmtpHost());
        m.put("smtpPort", s.getSmtpPort() == null ? 587 : s.getSmtpPort());
        m.put("smtpUsername", s.getSmtpUsername() == null ? "" : s.getSmtpUsername());
        m.put("smtpFrom", s.getSmtpFrom() == null ? "" : s.getSmtpFrom());
        m.put("smtpAuth", s.isSmtpAuth());
        m.put("smtpStartTls", s.isSmtpStartTls());
        m.put("smtpPasswordSet", s.getSmtpPassword() != null && !s.getSmtpPassword().isBlank());
        m.put("products", products.findAll().stream().map(catalog::card).toList());
        return m;
    }

    @Transactional
    public Map<String, Object> saveBrand(Map<String, Object> body, String actor) {
        PortalSettings s = current();
        if (body.get("brandName") != null) {
            String name = body.get("brandName").toString().trim();
            if (name.isBlank()) {
                throw new IllegalArgumentException("Enter a brand name.");
            }
            s.setBrandName(name);
        }
        if (body.get("primaryColor") != null) s.setPrimaryColor(hex(body.get("primaryColor")));
        if (body.get("hoverColor") != null) s.setHoverColor(hex(body.get("hoverColor")));
        if (body.get("softColor") != null) s.setSoftColor(hex(body.get("softColor")));
        if (body.get("inkColor") != null) s.setInkColor(hex(body.get("inkColor")));
        settings.save(s);
        audit.record(actor, "PORTAL_BRAND", "PORTAL", "1", s.getBrandName());
        return publicBranding();
    }

    @Transactional
    public Map<String, Object> saveLogo(MultipartFile file, String actor) {
        String url = files.store("brand", file);
        PortalSettings s = current();
        s.setLogoUrl(url);
        settings.save(s);
        audit.record(actor, "PORTAL_LOGO", "PORTAL", "1", url);
        return publicBranding();
    }

    @Transactional
    public Map<String, Object> saveProductImage(Long productId, MultipartFile file, String actor) {
        Product p = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        String url = files.store("products", file);
        p.setImageUrl(url);
        products.save(p);
        audit.record(actor, "PRODUCT_IMAGE", "PRODUCT", p.getSku(), url);
        return catalog.card(p);
    }

    @Transactional
    public Map<String, Object> saveSmtp(Map<String, Object> body, String actor) {
        PortalSettings s = current();
        if (body.get("smtpHost") != null) s.setSmtpHost(blankToNull(body.get("smtpHost")));
        if (body.get("smtpPort") != null && !body.get("smtpPort").toString().isBlank()) {
            s.setSmtpPort(Integer.valueOf(body.get("smtpPort").toString()));
        }
        if (body.get("smtpUsername") != null) s.setSmtpUsername(blankToNull(body.get("smtpUsername")));
        if (body.get("smtpFrom") != null) s.setSmtpFrom(blankToNull(body.get("smtpFrom")));
        if (body.get("smtpAuth") != null) s.setSmtpAuth(Boolean.parseBoolean(body.get("smtpAuth").toString()));
        if (body.get("smtpStartTls") != null) s.setSmtpStartTls(Boolean.parseBoolean(body.get("smtpStartTls").toString()));
        if (body.get("smtpPassword") != null && !body.get("smtpPassword").toString().isBlank()) {
            s.setSmtpPassword(body.get("smtpPassword").toString());
        }
        settings.save(s);
        audit.record(actor, "PORTAL_SMTP", "PORTAL", "1", s.getSmtpHost());
        return adminView();
    }

    public Map<String, Object> sendTestMail(String to, String actor) {
        PortalSettings s = current();
        if (s.getSmtpHost() == null || s.getSmtpHost().isBlank()) {
            throw new IllegalArgumentException("Enter an SMTP host first.");
        }
        String dest = to == null || to.isBlank() ? s.getSmtpFrom() : to.trim();
        if (dest == null || dest.isBlank()) {
            throw new IllegalArgumentException("Enter a test email address.");
        }
        try {
            JavaMailSenderImpl mail = new JavaMailSenderImpl();
            mail.setHost(s.getSmtpHost());
            mail.setPort(s.getSmtpPort() == null ? 587 : s.getSmtpPort());
            if (s.getSmtpUsername() != null) mail.setUsername(s.getSmtpUsername());
            if (s.getSmtpPassword() != null) mail.setPassword(s.getSmtpPassword());
            Properties props = mail.getJavaMailProperties();
            props.put("mail.smtp.auth", String.valueOf(s.isSmtpAuth()));
            props.put("mail.smtp.starttls.enable", String.valueOf(s.isSmtpStartTls()));
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");
            if (s.getSmtpPort() != null && s.getSmtpPort() == 465) {
                props.put("mail.smtp.ssl.enable", "true");
            }
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            String from = s.getSmtpFrom() == null || s.getSmtpFrom().isBlank() ? dest : s.getSmtpFrom();
            helper.setFrom(from);
            helper.setTo(dest);
            helper.setSubject(s.getBrandName() + " test email");
            helper.setText("This is a test message from the Rising Crescent portal admin.", false);
            mail.send(message);
            audit.record(actor, "PORTAL_SMTP_TEST", "PORTAL", "1", dest);
            return Map.of("sent", true, "to", dest);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("The test email could not be sent. Check host, port, and password.");
        }
    }

    private PortalSettings current() {
        return settings.findById(SETTINGS_ID).orElseGet(() -> settings.save(defaults()));
    }

    private static PortalSettings defaults() {
        return PortalSettings.builder()
                .id(SETTINGS_ID)
                .brandName("Rising Crescent")
                .primaryColor("#16a34a")
                .hoverColor("#15803d")
                .softColor("#dcfce7")
                .inkColor("#14532d")
                .smtpPort(587)
                .smtpAuth(true)
                .smtpStartTls(true)
                .build();
    }

    private static String hex(Object raw) {
        String v = raw == null ? "" : raw.toString().trim();
        if (!HEX.matcher(v).matches()) {
            throw new IllegalArgumentException("Use a hex color like #16a34a.");
        }
        return v.toLowerCase();
    }

    private static String blankToNull(Object raw) {
        if (raw == null) return null;
        String v = raw.toString().trim();
        return v.isBlank() ? null : v;
    }
}

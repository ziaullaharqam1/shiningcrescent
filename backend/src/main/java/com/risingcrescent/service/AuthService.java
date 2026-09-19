package com.risingcrescent.service;

import com.risingcrescent.audit.AuditService;
import com.risingcrescent.config.GoogleProperties;
import com.risingcrescent.domain.entity.Role;
import com.risingcrescent.domain.entity.UserAccount;
import com.risingcrescent.domain.enums.Channel;
import com.risingcrescent.repo.RoleRepository;
import com.risingcrescent.repo.UserAccountRepository;
import com.risingcrescent.security.JwtService;
import com.risingcrescent.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final OtpService otp;
    private final GoogleProperties googleProps;
    private final SmsGateway sms;
    private final PortalService portal;

    @Transactional
    public Map<String, Object> login(String username, String password) {
        UserAccount user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Username or password is not correct."));
        if (!user.isActive() || user.getPasswordHash() == null
                || !encoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Username or password is not correct.");
        }
        return completeLogin(user, "Successful login");
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String fullName, String email, String phone) {
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("That username is already in use. Try another.");
        }
        String normalizedPhone = null;
        if (phone != null && !phone.isBlank()) {
            normalizedPhone = PhoneNumbers.normalize(phone);
            if (users.findByPhone(normalizedPhone).isPresent()) {
            throw new IllegalArgumentException("That mobile number is already registered. Sign in with it instead.");
            }
        }
        Role customer = roles.findByCode("CUSTOMER").orElseThrow();
        UserAccount user = UserAccount.builder()
                .username(username)
                .passwordHash(encoder.encode(password))
                .fullName(fullName)
                .email(email)
                .phone(normalizedPhone)
                .active(true)
                .roles(new HashSet<>(Set.of(customer)))
                .build();
        users.save(user);
        audit.record(username, "REGISTER", "USER", String.valueOf(user.getId()), "Customer self-registration");
        return tokenPayload(user);
    }

    public Map<String, Object> publicConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean googleOn = googleProps.configured();
        m.put("googleEnabled", googleOn);
        m.put("googleLoginUrl", googleOn ? googleProps.loginUrlOrDefault() : "/oauth2/authorization/google");
        m.put("googleRedirectUri", googleProps.redirectUriOrDefault());
        try {
            m.put("smsMock", sms.mockMode());
        } catch (Exception ex) {
            m.put("smsMock", true);
        }
        try {
            m.putAll(portal.publicBranding());
        } catch (Exception ignored) {
            m.put("brandName", "Rising Crescent");
        }
        return m;
    }

    @Transactional
    public Map<String, Object> sendOtp(String phone) {
        return otp.send(phone);
    }

    @Transactional
    public Map<String, Object> verifyOtp(String rawPhone, String code, String fullName) {
        String phone = otp.consume(rawPhone, code);
        UserAccount user = users.findByPhone(phone).orElseGet(() -> createPhoneCustomer(phone, fullName));
        if (!user.isActive()) {
            throw new IllegalArgumentException("This account is disabled. Please contact support.");
        }
        if (user.getPhone() == null) {
            user.setPhone(phone);
        }
        return completeLogin(user, "OTP login");
    }

    @Transactional
    public Map<String, Object> loginGoogleUser(String subject, String email, String name) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google did not share an email. Allow email access and try again.");
        }
        UserAccount user = users.findByGoogleSub(subject)
                .or(() -> users.findByEmailIgnoreCase(email))
                .orElseGet(() -> createGoogleCustomer(subject, email, name));
        if (!user.isActive()) {
            throw new IllegalArgumentException("This account is disabled. Please contact support.");
        }
        if (user.getGoogleSub() == null && subject != null) {
            user.setGoogleSub(subject);
        }
        if ((user.getFullName() == null || user.getFullName().isBlank()) && name != null) {
            user.setFullName(name);
        }
        return completeLogin(user, "Google login");
    }

    public Map<String, Object> me(String username) {
        return users.findByUsername(username).map(this::profile)
                .orElseThrow(() -> new IllegalArgumentException("We could not find your account. Please sign in again."));
    }

    private Map<String, Object> completeLogin(UserAccount user, String detail) {
        user.setLastLoginAt(Instant.now());
        users.save(user);
        audit.record(user.getUsername(), "LOGIN", "USER", String.valueOf(user.getId()), detail);
        return tokenPayload(user);
    }

    private UserAccount createPhoneCustomer(String phone, String fullName) {
        String name = (fullName == null || fullName.isBlank()) ? "Shopper " + phone.substring(Math.max(0, phone.length() - 4)) : fullName.trim();
        String username = uniqueUsername("m" + phone.replace("+", ""));
        UserAccount user = UserAccount.builder()
                .username(username)
                .passwordHash(encoder.encode(UUID.randomUUID().toString()))
                .fullName(name)
                .email(username + "@phone.risingcrescent.local")
                .phone(phone)
                .active(true)
                .preferredChannel(Channel.RETAIL)
                .roles(new HashSet<>(Set.of(customerRole())))
                .build();
        return users.save(user);
    }

    private UserAccount createGoogleCustomer(String subject, String email, String name) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.isBlank()) {
            base = "guser";
        }
        String username = uniqueUsername(base.length() > 40 ? base.substring(0, 40) : base);
        String display = (name == null || name.isBlank()) ? email : name;
        UserAccount user = UserAccount.builder()
                .username(username)
                .passwordHash(encoder.encode(UUID.randomUUID().toString()))
                .fullName(display)
                .email(email)
                .googleSub(subject)
                .active(true)
                .preferredChannel(Channel.RETAIL)
                .roles(new HashSet<>(Set.of(customerRole())))
                .build();
        return users.save(user);
    }

    private Role customerRole() {
        return roles.findByCode("CUSTOMER").orElseThrow();
    }

    private String uniqueUsername(String preferred) {
        String u = preferred;
        int i = 0;
        while (users.existsByUsername(u)) {
            i++;
            u = preferred + i;
        }
        return u;
    }

    private Map<String, Object> tokenPayload(UserAccount user) {
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).toList();
        Map<String, Object> body = new LinkedHashMap<>(profile(user));
        body.put("token", jwt.issue(user.getUsername(), roleCodes));
        return body;
    }

    private Map<String, Object> profile(UserAccount user) {
        Set<String> perms = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("fullName", user.getFullName());
        m.put("email", user.getEmail());
        m.put("phone", user.getPhone());
        m.put("channel", user.getPreferredChannel());
        m.put("companyName", user.getCompanyName());
        m.put("city", user.getCity());
        m.put("creditLimit", user.getCreditLimit());
        m.put("roles", roleCodes);
        m.put("permissions", perms);
        m.put("console", roleCodes.stream().anyMatch(r -> !"CUSTOMER".equals(r)));
        return m;
    }
}

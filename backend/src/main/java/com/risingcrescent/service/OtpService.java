package com.risingcrescent.service;

import com.risingcrescent.domain.entity.OtpChallenge;
import com.risingcrescent.repo.OtpChallengeRepository;
import com.risingcrescent.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OtpService {
    static final int CODE_LENGTH = 6;
    static final int TTL_MINUTES = 5;
    static final int MAX_ATTEMPTS = 5;
    static final int COOLDOWN_SECONDS = 45;
    static final int MAX_SENDS_PER_HOUR = 8;

    private final OtpChallengeRepository challenges;
    private final PasswordEncoder encoder;
    private final SmsGateway sms;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public Map<String, Object> send(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        Instant now = Instant.now();
        if (challenges.countByPhoneAndSentAtAfter(phone, now.minus(1, ChronoUnit.HOURS)) >= MAX_SENDS_PER_HOUR) {
            throw new IllegalArgumentException("Too many codes sent to this number. Try again later.");
        }
        challenges.findFirstByPhoneAndConsumedFalseAndExpiresAtAfterOrderBySentAtDesc(phone, now)
                .ifPresent(latest -> {
                    if (latest.getSentAt() != null
                            && latest.getSentAt().plusSeconds(COOLDOWN_SECONDS).isAfter(now)) {
                        throw new IllegalArgumentException("Wait a moment before requesting another code");
                    }
                });
        challenges.findByPhoneAndConsumedFalse(phone).forEach(c -> {
            c.setConsumed(true);
            challenges.save(c);
        });
        String code = String.format("%0" + CODE_LENGTH + "d", random.nextInt(1_000_000));
        OtpChallenge row = OtpChallenge.builder()
                .phone(phone)
                .codeHash(encoder.encode(code))
                .expiresAt(now.plus(TTL_MINUTES, ChronoUnit.MINUTES))
                .sentAt(now)
                .build();
        challenges.save(row);
        SmsGateway.SendResult smsResult = sms.sendOtp(phone, code);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sent", true);
        m.put("phone", phone);
        m.put("expiresInSeconds", TTL_MINUTES * 60);
        m.put("mock", smsResult.mock());
        if (smsResult.mock()) {
            m.put("devCode", code);
        }
        return m;
    }

    @Transactional
    public String consume(String rawPhone, String rawCode) {
        String phone = PhoneNumbers.normalize(rawPhone);
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{" + CODE_LENGTH + "}")) {
            throw new IllegalArgumentException("Enter the 6-digit code");
        }
        Instant now = Instant.now();
        OtpChallenge challenge = challenges
                .findFirstByPhoneAndConsumedFalseAndExpiresAtAfterOrderBySentAtDesc(phone, now)
                .orElseThrow(() -> new IllegalArgumentException("Code expired. Request a new one."));
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            challenge.setConsumed(true);
            challenges.save(challenge);
            throw new IllegalArgumentException("Too many attempts. Request a new code.");
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (!encoder.matches(code, challenge.getCodeHash())) {
            challenges.save(challenge);
            throw new IllegalArgumentException("That code is not correct");
        }
        challenge.setConsumed(true);
        challenges.save(challenge);
        return phone;
    }
}

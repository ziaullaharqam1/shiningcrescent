package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {
    Optional<OtpChallenge> findFirstByPhoneAndConsumedFalseAndExpiresAtAfterOrderBySentAtDesc(String phone, Instant now);

    List<OtpChallenge> findByPhoneAndConsumedFalse(String phone);

    long countByPhoneAndSentAtAfter(String phone, Instant after);
}

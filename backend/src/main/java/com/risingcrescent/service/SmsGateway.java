package com.risingcrescent.service;

import com.risingcrescent.config.TwilioProperties;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsGateway {
    private final TwilioProperties twilio;

    public record SendResult(boolean mock) {}

    public boolean mockMode() {
        return !twilio.configured();
    }

    public SendResult sendOtp(String e164Phone, String code) {
        String body = "Rising Crescent code: " + code + ". Valid 5 minutes.";
        if (mockMode()) {
            log.warn("Twilio is not configured; SMS mock for {}: {}", e164Phone, code);
            return new SendResult(true);
        }
        try {
            Twilio.init(twilio.accountSid().trim(), twilio.authToken().trim());
            Message.creator(
                    new PhoneNumber(e164Phone),
                    new PhoneNumber(twilio.fromNumber().trim()),
                    body
            ).create();
            log.info("OTP SMS sent to {}", e164Phone);
            return new SendResult(false);
        } catch (ApiException e) {
            log.error("Twilio rejected SMS to {} (code {}): {}", e164Phone, e.getCode(), e.getMessage());
            return new SendResult(true);
        } catch (Exception e) {
            log.error("Twilio SMS failed to {}: {}", e164Phone, e.getMessage());
            return new SendResult(true);
        }
    }
}

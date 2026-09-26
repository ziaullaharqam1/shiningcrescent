package com.shiningcrescent.web;

import com.shiningcrescent.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record RegisterRequest(@NotBlank String username, @NotBlank String password,
                                  @NotBlank String fullName, @NotBlank String email, String phone) {}

    public record OtpSendRequest(@NotBlank String phone) {}

    public record OtpVerifyRequest(@NotBlank String phone, @NotBlank String code, String fullName) {}

    @GetMapping("/public-config")
    public Map<String, Object> publicConfig() {
        return auth.publicConfig();
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.username(), req.password());
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req.username(), req.password(), req.fullName(), req.email(), req.phone());
    }

    @PostMapping("/otp/send")
    public Map<String, Object> sendOtp(@Valid @RequestBody OtpSendRequest req) {
        return auth.sendOtp(req.phone());
    }

    @PostMapping("/otp/verify")
    public Map<String, Object> verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        return auth.verifyOtp(req.phone(), req.code(), req.fullName());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Principal principal) {
        return auth.me(principal.getName());
    }
}

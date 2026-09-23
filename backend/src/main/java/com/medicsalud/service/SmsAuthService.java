package com.medicsalud.service;

import com.medicsalud.model.User;
import com.medicsalud.repository.UserRepository;
import com.medicsalud.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
public class SmsAuthService {
    private static final int CODE_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsSender smsSender;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public SmsAuthService(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          SmsSender smsSender,
                          JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsSender = smsSender;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public void requestCode(String username) {
        userRepository.findByUsername(normalizeUsername(username)).ifPresent(user -> {
            if (!"ROLE_ADMIN".equalsIgnoreCase(user.getRole()) || !hasValidPhone(user) || isInCooldown(user)) {
                return;
            }

            String code = String.format("%0" + CODE_LENGTH + "d", secureRandom.nextInt(1_000_000));
            user.setSmsCodeHash(passwordEncoder.encode(code));
            user.setSmsCodeExpiresAt(Instant.now().plus(CODE_TTL));
            user.setSmsCodeSentAt(Instant.now());
            user.setSmsCodeAttempts(0);
            userRepository.save(user);
            smsSender.send(user.getPhoneNumber(), "Tu código de acceso MedicSalud es: " + code);
        });
    }

    @Transactional
    public AuthService.AuthResponse authenticateWithSms(String username, String code) {
        User user = userRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("Código inválido"));

        if (!"ROLE_ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("Código SMS no habilitado para este usuario");
        }

        if (code == null || !code.matches("\\d{" + CODE_LENGTH + "}")) {
            throw new IllegalArgumentException("Código inválido");
        }
        if (user.getSmsCodeHash() == null || user.getSmsCodeExpiresAt() == null
                || Instant.now().isAfter(user.getSmsCodeExpiresAt())) {
            throw new IllegalArgumentException("Código expirado");
        }
        if (user.getSmsCodeAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Demasiados intentos");
        }

        user.setSmsCodeAttempts(user.getSmsCodeAttempts() + 1);
        if (!passwordEncoder.matches(code, user.getSmsCodeHash())) {
            userRepository.save(user);
            throw new IllegalArgumentException("Código inválido");
        }

        String role = normalizeRole(user.getRole());
        if (!"ROLE_ADMIN".equals(role) && !"ROLE_PERSONAL".equals(role)) {
            throw new IllegalArgumentException("Acceso no autorizado");
        }

        user.setSmsCodeHash(null);
        user.setSmsCodeExpiresAt(null);
        user.setSmsCodeSentAt(null);
        user.setSmsCodeAttempts(0);
        userRepository.save(user);

        return new AuthService.AuthResponse(
                jwtTokenProvider.createAccessToken(user.getUsername(), role, true),
                jwtTokenProvider.createRefreshToken(user.getUsername()),
                role);
    }

    private boolean hasValidPhone(User user) {
        return user.getPhoneNumber() != null
                && user.getPhoneNumber().matches("\\+[1-9]\\d{7,14}");
    }

    private boolean isInCooldown(User user) {
        return user.getSmsCodeSentAt() != null
                && Instant.now().isBefore(user.getSmsCodeSentAt().plus(RESEND_COOLDOWN));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "ROLE_PERSONAL";
        }
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}

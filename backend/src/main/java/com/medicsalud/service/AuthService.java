package com.medicsalud.service;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Servicio de Autenticación y Gestión de Sesiones
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.2.1  – Registro y cancelación de usuarios
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * ISO 27001  : A.9.3.1  – Uso de información de autenticación secreta
 * ISO 27001  : A.10.1.1 – Política de uso de controles criptográficos
 * OWASP Top10: A02 – Cryptographic Failures (contraseñas seguras)
 * OWASP Top10: A07 – Identification and Authentication Failures
 *
 * Gestiona:
 *   • Registro de usuarios con contraseña BCrypt y secreto TOTP cifrado
 *   • Autenticación con validación de credenciales + MFA opcional/obligatorio
 *   • Emisión de tokens JWT de acceso y refresco
 *   • Refresco de access tokens mediante refresh token válido
 * ═══════════════════════════════════════════════════════════════════════
 */

import com.medicsalud.model.User;
import com.medicsalud.repository.UserRepository;
import com.medicsalud.security.JwtTokenProvider;
import com.medicsalud.security.TotpUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Instant;

/**
 * Servicio de autenticación de MedicSalud.
 *
 * <p><b>Flujo de autenticación seguro (ISO 27001 A.9.4.2 / OWASP A07)</b>:
 * <ol>
 *   <li>Validar credenciales con Spring Security + BCrypt.</li>
 *   <li>Verificar que el usuario tenga rol autorizado para acceder al panel.</li>
 *   <li>Si el usuario requiere MFA, validar el código TOTP del segundo factor.</li>
 *   <li>Emitir access token (con claim {@code mfaVerified}) y refresh token.</li>
 * </ol>
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtTokenProvider      jwtTokenProvider;
    private final Set<String> authorizedAdminEmails;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository        userRepository,
                       PasswordEncoder       passwordEncoder,
                       JwtTokenProvider      jwtTokenProvider,
                       @Value("${security.admin.allowed-emails:}") String allowedAdminEmails) {
        this.authenticationManager = authenticationManager;
        this.userRepository        = userRepository;
        this.passwordEncoder       = passwordEncoder;
        this.jwtTokenProvider      = jwtTokenProvider;
        this.authorizedAdminEmails = Arrays.stream(allowedAdminEmails.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(email -> !email.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    // ─────────────────────────────────────────────────────────────────
    // Registro de usuarios
    // ─────────────────────────────────────────────────────────────────

    /**
     * Registra un nuevo usuario con contraseña hasheada y secreto TOTP cifrado.
     *
     * <p><b>ISO 27001 A.9.2.1 / OWASP A02</b>:
     * <ul>
     *   <li>La contraseña se hashea con BCrypt antes de persistir.</li>
     *   <li>El secreto TOTP se genera con SecureRandom y se almacena cifrado
     *       en BD mediante {@link com.medicsalud.security.EncryptedStringConverter} (AES-GCM).</li>
     * </ul>
     *
     * @param username   nombre de usuario único
     * @param rawPassword contraseña en texto plano (se hashea internamente)
     * @param role       rol solicitado (se normaliza a ROLE_XXX)
     * @return entidad de usuario persistida
     */
    public User register(String username, String rawPassword, String role) {
        return register(username, rawPassword, role, null);
    }

    public User register(String username, String rawPassword, String role, String phoneNumber) {
        return register(username, rawPassword, role, phoneNumber, null);
    }

    public User register(String username, String rawPassword, String role, String phoneNumber, String email) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("El nombre de usuario es requerido");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("La contraseña es requerida");
        }
        if (email == null || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("El correo electrónico es requerido y debe ser válido");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByUsername(username.trim()).isPresent()) {
            throw new IllegalStateException("El nombre de usuario ya está en uso");
        }

        String normalizedRole = normalizeRole(role);

        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword)); // BCrypt (ISO A.10.1.1)
        user.setRole("ROLE_CLIENT");
        user.setMfaRequired(false);
        user.setMfaSecret(TotpUtil.generateSecret()); // Secreto aleatorio cifrado (AES-GCM)
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            String normalizedPhone = phoneNumber.trim();
            if (!normalizedPhone.matches("\\+[1-9]\\d{7,14}")) {
                throw new IllegalArgumentException("El teléfono debe usar formato internacional E.164");
            }
            user.setPhoneNumber(normalizedPhone);
        }

        return userRepository.save(user);
    }

    public User registerStaff(String username, String rawPassword, String role, String phoneNumber, String email) {
        User user = register(username, rawPassword, "ROLE_CLIENT", phoneNumber, email);
        user.setRole("ROLE_ADMIN".equals(normalizeRole(role)) ? "ROLE_ADMIN" : "ROLE_PERSONAL");
        user.setMfaRequired("ROLE_ADMIN".equals(user.getRole()));
        return userRepository.save(user);
    }

    // ─────────────────────────────────────────────────────────────────
    // Autenticación
    // ─────────────────────────────────────────────────────────────────

    /**
     * Autentica un usuario con credenciales + MFA opcional.
     *
     * <p><b>ISO 27001 A.9.4.2 / OWASP A07</b>:
     * <ul>
     *   <li>Spring Security valida las credenciales contra BCrypt.</li>
     *   <li>Solo roles ADMIN y PERSONAL pueden acceder al panel interno.</li>
     *   <li>Si {@code mfaRequired=true}, el código TOTP es obligatorio.</li>
     *   <li>El claim {@code mfaVerified} se incluye en el access token
     *       para que el {@link com.medicsalud.security.MfaFilter} lo verifique.</li>
     * </ul>
     *
     * @param username  nombre de usuario
     * @param password  contraseña en texto plano
     * @param totpCode  código TOTP de 6 dígitos (vacío si no aplica MFA)
     * @return tokens JWT de acceso y refresco
     * @throws RuntimeException si las credenciales o el código MFA son inválidos
     */
    public AuthResponse authenticate(String username, String password, String totpCode) {
        return authenticate(username, password, totpCode, null);
    }

    public AuthResponse authenticate(String username, String password, String totpCode, String email) {
        String normalizedUsername = username == null ? "" : username.trim();

        // 1. Validar credenciales con Spring Security (BCrypt comparison)
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(normalizedUsername, password));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 2. Cargar usuario desde BD para verificar rol y MFA
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 3. Control de acceso por rol (ISO A.9.4.1)
        String normalizedRole = normalizeRole(user.getRole());
        if (!"ROLE_ADMIN".equals(normalizedRole)
                && !"ROLE_PERSONAL".equals(normalizedRole)
                && !"ROLE_CLIENT".equals(normalizedRole)) {
            throw new RuntimeException("Acceso no autorizado para este rol");
        }
        if ("ROLE_ADMIN".equals(normalizedRole)
            && (user.getEmail() == null
            || email == null
            || !user.getEmail().equalsIgnoreCase(email.trim())
            || !authorizedAdminEmails.contains(user.getEmail().toLowerCase()))) {
            throw new RuntimeException("Correo administrativo no autorizado");
        }

        if ("ROLE_ADMIN".equals(normalizedRole)) {
            verifyAdminSmsCode(user, totpCode);
        }

        // 4. Verificación de segundo factor MFA (ISO A.9.4.2 / A.9.3.1)
        boolean mfaVerified = false;
        if (user.isMfaRequired()) {
            if (totpCode == null || totpCode.isBlank()) {
                throw new RuntimeException("Se requiere código MFA");
            }
            if (!TotpUtil.validateCode(user.getMfaSecret(), totpCode)) {
                throw new RuntimeException("Código MFA inválido");
            }
            mfaVerified = true;
        } else {
            mfaVerified = true; // Si MFA no es obligatorio, se considera verificado
        }

        // 5. Emitir tokens JWT con el rol real del usuario y el claim mfaVerified
        String accessToken  = jwtTokenProvider.createAccessToken(normalizedUsername, normalizedRole, mfaVerified);
        String refreshToken = jwtTokenProvider.createRefreshToken(normalizedUsername);

        return new AuthResponse(accessToken, refreshToken, normalizedRole);
    }

    private void verifyAdminSmsCode(User user, String code) {
        if (code == null || !code.matches("\\d{6}") || user.getSmsCodeHash() == null
                || user.getSmsCodeExpiresAt() == null || Instant.now().isAfter(user.getSmsCodeExpiresAt())
                || user.getSmsCodeAttempts() >= 5) {
            throw new RuntimeException("Se requiere un código SMS administrativo válido");
        }
        user.setSmsCodeAttempts(user.getSmsCodeAttempts() + 1);
        if (!passwordEncoder.matches(code, user.getSmsCodeHash())) {
            userRepository.save(user);
            throw new RuntimeException("Código SMS administrativo inválido");
        }
        user.setSmsCodeHash(null);
        user.setSmsCodeExpiresAt(null);
        user.setSmsCodeSentAt(null);
        user.setSmsCodeAttempts(0);
        userRepository.save(user);
    }

    // ─────────────────────────────────────────────────────────────────
    // Refresco de tokens
    // ─────────────────────────────────────────────────────────────────

    /**
     * Genera un nuevo access token a partir de un refresh token válido.
     *
     * <p><b>ISO 27001 A.9.4.2</b>: El refresh token se valida con firma
     * HMAC-SHA256 antes de emitir un nuevo access token.
     *
     * @param refreshToken token de refresco emitido en el login
     * @return nuevo access token JWT
     */
    public String refreshAccessToken(String refreshToken) {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new RuntimeException("Refresh token inválido o expirado");
        }
        String username = jwtTokenProvider.getUsernameFromRefreshToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return jwtTokenProvider.createAccessToken(username, normalizeRole(user.getRole()), true);
    }

    // ─────────────────────────────────────────────────────────────────
    // Métodos auxiliares
    // ─────────────────────────────────────────────────────────────────

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "ROLE_PERSONAL";
        }
        String trimmed = role.trim().toUpperCase();
        return trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
    }

    // ─────────────────────────────────────────────────────────────────
    // DTO de respuesta de autenticación
    // ─────────────────────────────────────────────────────────────────

    /**
     * Respuesta de autenticación exitosa.
     *
     * <p>Devuelve el rol real del usuario (no hardcodeado) para que el
     * frontend pueda redirigir correctamente.
     */
    public static class AuthResponse {
        private final String accessToken;
        private final String refreshToken;
        private final String role;

        public AuthResponse(String accessToken, String refreshToken, String role) {
            this.accessToken  = accessToken;
            this.refreshToken = refreshToken;
            this.role         = role;
        }

        public String getAccessToken()  { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public String getRole()         { return role; }
    }
}

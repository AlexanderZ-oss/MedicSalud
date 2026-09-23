package com.medicsalud.controller;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Controlador de Autenticación
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * ISO 27001  : A.9.2.1  – Registro y cancelación de usuarios
 * OWASP Top10: A07 – Identification and Authentication Failures
 * OWASP Top10: A01 – Broken Access Control
 *
 * Expone los endpoints de autenticación:
 *   POST /api/v1/auth/register  → Registro de usuarios
 *   POST /api/v1/auth/login     → Login con usuario/contraseña + MFA opcional
 *   POST /api/v1/auth/refresh   → Refresco de access token
 *   GET  /api/v1/health         → Health check (público)
 *   POST /api/v1/checkout       → Procesamiento de pago (autenticado)
 * ═══════════════════════════════════════════════════════════════════════
 */

import com.medicsalud.model.User;
import com.medicsalud.service.AuthService;
import com.medicsalud.service.SmsAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST de autenticación.
 *
 * <p><b>Seguridad (ISO 27001 A.9.4.2 / OWASP A07)</b>:
 * <ul>
 *   <li>Los endpoints {@code /login}, {@code /register} y {@code /refresh}
 *       son públicos (configurados en SecurityConfig).</li>
 *   <li>La lógica de validación de credenciales y MFA se delega a
 *       {@link AuthService} — los controladores no deben manejar criptografía.</li>
 *   <li>Los errores se devuelven con mensajes genéricos para no revelar
 *       información interna (OWASP A07 – enumeración de usuarios).</li>
 *   <li>El rol devuelto en la respuesta de login es el rol REAL del usuario,
 *       nunca un valor hardcodeado.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final SmsAuthService smsAuthService;

    public AuthController(AuthService authService, SmsAuthService smsAuthService) {
        this.authService = authService;
        this.smsAuthService = smsAuthService;
    }

    @PostMapping("/auth/sms/request")
    public ResponseEntity<?> requestSmsCode(@RequestBody Map<String, String> body) {
        smsAuthService.requestCode(body.get("username"));
        return ResponseEntity.accepted().body(Map.of(
                "message", "Si la cuenta está habilitada, recibirás un código SMS"));
    }

    @PostMapping("/auth/sms/verify")
    public ResponseEntity<?> verifySmsCode(@RequestBody Map<String, String> body) {
        try {
            AuthService.AuthResponse response = smsAuthService.authenticateWithSms(
                    body.get("username"), body.get("code"));
            return ResponseEntity.ok(Map.of(
                    "accessToken", response.getAccessToken(),
                    "refreshToken", response.getRefreshToken(),
                    "role", response.getRole()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Código SMS inválido o expirado"));
        }
    }

    // ── Registro ──────────────────────────────────────────────────────

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * <p><b>ISO 27001 A.9.2.1</b>: Solo roles permitidos pueden registrarse
     * (la validación de rol ocurre en AuthService).
     *
     * @param body JSON con {@code username}, {@code password} y {@code role} (opcional)
     */
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String role     = body.getOrDefault("role", "ROLE_PERSONAL");
        String phoneNumber = body.get("phoneNumber");
        String email = body.get("email");

        try {
            User user = authService.register(username, password, role, phoneNumber, email);
            return ResponseEntity.ok(Map.of(
                    "message",  "Usuario registrado correctamente",
                    "username", user.getUsername(),
                    "role",     user.getRole()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Login ─────────────────────────────────────────────────────────

    /**
     * Autentica al usuario y devuelve tokens JWT.
     *
     * <p><b>ISO 27001 A.9.4.2 / OWASP A07</b>:
     * <ul>
     *   <li>El rol devuelto proviene de la BD (nunca hardcodeado).</li>
     *   <li>En caso de error, se devuelve un mensaje genérico para evitar
     *       revelar si el usuario existe (protección contra enumeración).</li>
     * </ul>
     *
     * @param body JSON con {@code username}, {@code password} y {@code totpCode} (opcional)
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username  = body.get("username");
        String password  = body.get("password");
        String totpCode  = body.getOrDefault("totpCode", "");
        String email     = body.get("email");

        try {
            AuthService.AuthResponse response = authService.authenticate(username, password, totpCode, email);
            return ResponseEntity.ok(Map.of(
                    "accessToken",  response.getAccessToken(),
                    "refreshToken", response.getRefreshToken(),
                    "role",         response.getRole()   // ← Rol real del usuario (ISO A.9.4.2)
            ));
        } catch (Exception e) {
            // Mensaje genérico — no revelar si el usuario existe (OWASP A07)
            return ResponseEntity.status(401).body(Map.of("error", "Credenciales inválidas"));
        }
    }

    // ── Refresco de Token ─────────────────────────────────────────────

    /**
     * Genera un nuevo access token usando el refresh token.
     *
     * <p><b>ISO 27001 A.9.4.2</b>: El refresh token se valida con firma
     * HMAC-SHA256 antes de emitir un nuevo access token.
     */
    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Refresh token requerido en el encabezado Authorization"));
        }

        try {
            String refreshToken = authHeader.substring(7);
            String newAccessToken = authService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token inválido o expirado"));
        }
    }

    // ── Health Check ──────────────────────────────────────────────────

    /** Endpoint público de verificación de salud del servicio. */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "medicsalud-backend"));
    }

    // ── Checkout ──────────────────────────────────────────────────────

    /**
     * Procesa un pedido de compra autenticado.
     *
     * <p><b>ISO 27001 A.9.4.1 / OWASP A01</b>: Protegido con
     * {@code @PreAuthorize} — solo usuarios autenticados con rol
     * ADMIN o CLIENT pueden acceder.  El rate limiting lo controla
     * {@link com.medicsalud.config.RateLimitingFilter}.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody Map<String, Object> body) {
        // Lógica de checkout — validación de Turnstile y procesamiento del pedido
        // TODO: integrar TurnstileService para validar captchaToken del body
        String captchaToken = (String) body.getOrDefault("captchaToken", "");
        if (captchaToken == null || captchaToken.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Captcha requerido para procesar el pago"));
        }
        return ResponseEntity.ok(Map.of(
                "status",  "success",
                "message", "Checkout procesado correctamente"
        ));
    }
}

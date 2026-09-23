package com.medicsalud.security;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Autenticación y Autorización basada en JWT
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * ISO 27001  : A.9.1.2  – Acceso a redes y servicios de red
 * OWASP Top10: A07 – Identification and Authentication Failures
 * OWASP Top10: A01 – Broken Access Control
 *
 * Proveedor de tokens JWT firmados con HMAC-SHA256.
 * Los parámetros de validez se inyectan desde entorno para evitar
 * secretos hardcodeados (ISO 27001 A.10.1.2).
 * ═══════════════════════════════════════════════════════════════════════
 */

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * Genera y valida tokens JWT para acceso y refresco de sesión.
 *
 * <p><b>ISO 27001 A.9.4.2 / OWASP A07</b>:
 * <ul>
 *   <li>Firmados con HMAC-SHA-256 (mínimo recomendado para JWT).</li>
 *   <li>El secreto se inyecta desde ${security.jwt.secret} (variable de entorno).</li>
 *   <li>El token de acceso expira en ${security.jwt.access-expiration-ms} ms (1h por defecto).</li>
 *   <li>El token de refresco expira en ${security.jwt.refresh-expiration-ms} ms (24h por defecto).</li>
 *   <li>El claim "role" se incluye para autorización RBAC (A.9.1.2).</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    // ── Configuración inyectada desde application.properties / entorno ─
    // ISO 27001 A.10.1.2 – Secretos nunca hardcodeados en código fuente
    @Value("${security.jwt.secret}")
    private String secretKey;

    @Value("${security.jwt.access-expiration-ms}")
    private long accessTokenValidity;

    @Value("${security.jwt.refresh-expiration-ms}")
    private long refreshTokenValidity;

    private Key key;

    @PostConstruct
    public void init() {
        // Derivamos la clave HMAC desde el secreto configurado
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    /**
     * Crea un token JWT de acceso firmado que incluye usuario y rol.
     *
     * <p><b>OWASP A01 / ISO 27001 A.9.1.2</b>: El claim {@code role}
     * se incluye para permitir RBAC sin consultar la BD en cada petición.
     *
     * @param username nombre de usuario autenticado
     * @param role     rol normalizado (ROLE_ADMIN, ROLE_PERSONAL, etc.)
     * @return JWT compacto firmado con HS256
     */
    /**
     * Crea token de acceso con rol y estado de verificación MFA.
     *
     * <p>El claim {@code mfaVerified} es inspeccionado por {@link MfaFilter}
     * para proteger rutas privilegiadas (ISO 27001 A.9.4.2).
     *
     * @param username    nombre de usuario
     * @param role        rol RBAC (ROLE_ADMIN, ROLE_PERSONAL, etc.)
     * @param mfaVerified {@code true} si el usuario completó el segundo factor
     */
    public String createAccessToken(String username, String role, boolean mfaVerified) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)                    // ← RBAC claim (OWASP A01 / ISO A.9.1.2)
                .claim("mfaVerified", mfaVerified)      // ← MFA claim (ISO A.9.4.2)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenValidity))
                .signWith(key, SignatureAlgorithm.HS256) // ← HS256 (ISO A.10.1.1)
                .compact();
    }

    /** @deprecated Usar {@link #createAccessToken(String, String, boolean)} */
    @Deprecated
    public String createAccessToken(String username, String role) {
        return createAccessToken(username, role, false);
    }

    /**
     * Crea un token de refresco con mayor vida útil (sin claim de rol).
     *
     * <p>Se usa solo para obtener nuevos access tokens; no otorga acceso
     * por sí mismo a recursos protegidos.
     */
    public String createRefreshToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenValidity))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Valida la firma y vigencia del token de acceso.
     *
     * <p><b>OWASP A07</b>: Captura excepciones de firma, expiración y
     * formato malformado sin exponer detalles internos al cliente.
     */
    public boolean validateAccessToken(String token) {
        try {
            Jwts.parser().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            // No loguear el token completo (contiene datos del usuario)
            return false;
        }
    }

    /** @see #validateAccessToken(String) */
    public boolean validateRefreshToken(String token) {
        return validateAccessToken(token);
    }

    /** Extrae el nombre de usuario ({@code sub}) del token. */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** @see #getUsernameFromToken(String) */
    public String getUsernameFromRefreshToken(String token) {
        return getUsernameFromToken(token);
    }

    /**
     * Extrae el claim {@code role} del token de acceso.
     *
     * <p><b>ISO 27001 A.9.1.2</b>: El rol determina qué recursos puede
     * acceder el portador del token (RBAC).
     */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // ── Interno ───────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

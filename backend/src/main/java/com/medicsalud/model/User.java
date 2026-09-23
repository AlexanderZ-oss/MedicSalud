package com.medicsalud.model;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Entidad de Usuario con almacenamiento seguro de credenciales
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.2.1  – Registro y cancelación de usuarios
 * ISO 27001  : A.9.3.1  – Uso de información de autenticación secreta
 * ISO 27001  : A.10.1.1 – Política de uso de controles criptográficos
 * OWASP Top10: A02 – Cryptographic Failures (datos sensibles en BD)
 *
 * Campos de seguridad:
 *   • password   → BCrypt (nunca texto plano) — ISO A.9.3.1
 *   • mfaSecret  → Cifrado AES-256/GCM en BD (EncryptedStringConverter) — ISO A.10.1.1
 *   • mfaRequired → Determina si se exige segundo factor en el login
 *
 * Ubicación ISO de parámetros:
 *   • password:    columna "password" en tabla "users" — BCrypt hash
 *   • mfaSecret:   columna "mfa_secret" en tabla "users" — AES-256-GCM cifrado
 *   • mfaRequired: columna "mfa_required" — boolean (true para ADMIN obligatorio)
 * ═══════════════════════════════════════════════════════════════════════
 */

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad JPA que representa un usuario de la aplicación MedicSalud.
 *
 * <p><b>Almacenamiento seguro (ISO 27001 A.10.1.1 / OWASP A02)</b>:
 * <ul>
 *   <li>{@code password} — almacenado como hash BCrypt; nunca en texto plano.</li>
 *   <li>{@code mfaSecret} — secreto TOTP cifrado con AES-256/GCM mediante
 *       {@link com.medicsalud.security.EncryptedStringConverter}.</li>
 * </ul>
 *
 * <p><b>Parámetros en base de datos</b> (tabla {@code users}):
 * <table border="1">
 *   <tr><th>Columna</th><th>Tipo</th><th>Control de Seguridad</th><th>ISO 27001</th></tr>
 *   <tr><td>id</td><td>BIGINT PK</td><td>Identificador interno</td><td>—</td></tr>
 *   <tr><td>username</td><td>VARCHAR(50) UNIQUE</td><td>Nombre de usuario único</td><td>A.9.2.1</td></tr>
 *   <tr><td>password</td><td>VARCHAR</td><td>Hash BCrypt (coste 10)</td><td>A.9.3.1, A.10.1.1</td></tr>
 *   <tr><td>role</td><td>VARCHAR(20)</td><td>Rol RBAC (ROLE_ADMIN, etc.)</td><td>A.9.1.2</td></tr>
 *   <tr><td>mfa_secret</td><td>VARCHAR</td><td>Cifrado AES-256/GCM en BD</td><td>A.10.1.1, A.10.1.2</td></tr>
 *   <tr><td>mfa_required</td><td>BOOLEAN</td><td>Exige 2do factor en login</td><td>A.9.4.2</td></tr>
 * </table>
 */
@Entity
@Table(name = "users",
    indexes = @Index(name = "idx_users_username", columnList = "username"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Identificación del usuario ────────────────────────────────────
    // ISO 27001 A.9.2.1 – El nombre de usuario es único y no modificable
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(unique = true, length = 120)
    private String email;

    // ── Contraseña hasheada ───────────────────────────────────────────
    // ISO 27001 A.9.3.1 – Información de autenticación almacenada como hash
    // OWASP A02 – BCrypt; nunca texto plano ni MD5/SHA1
    @Column(nullable = false)
    private String password; // → BCrypt hash (ver AuthService.register)

    // ── Control de acceso basado en roles (RBAC) ──────────────────────
    // ISO 27001 A.9.1.2 – Roles: ROLE_ADMIN, ROLE_PERSONAL, ROLE_CLIENT
    @Column(nullable = false, length = 20)
    private String role;

    // ── Secreto TOTP para segundo factor (MFA) ────────────────────────
    // ISO 27001 A.10.1.1 – Almacenado cifrado con AES-256/GCM
    // ISO 27001 A.10.1.2 – La clave de cifrado se gestiona en entorno (no hardcoded)
    // La conversión es automática via EncryptedStringConverter
    @Convert(converter = com.medicsalud.security.EncryptedStringConverter.class)
    @Column(name = "mfa_secret")
    private String mfaSecret; // → Cifrado AES-256/GCM en BD

    // ── Indicador de MFA obligatorio ──────────────────────────────────
    // ISO 27001 A.9.4.2 – Control explícito del segundo factor
    @Column(name = "mfa_required", nullable = false)
    private boolean mfaRequired;

    @Column(name = "phone_number", length = 16)
    private String phoneNumber;

    @Column(name = "sms_code_hash")
    private String smsCodeHash;

    @Column(name = "sms_code_expires_at")
    private Instant smsCodeExpiresAt;

    @Column(name = "sms_code_sent_at")
    private Instant smsCodeSentAt;

    @Column(name = "sms_code_attempts", nullable = false)
    private int smsCodeAttempts;

    /**
     * Determina si se requiere MFA en el login.
     *
    * <p><b>ISO 27001 A.9.4.2</b>: El requisito se controla mediante
    * {@code mfaRequired}; el registro activa MFA automáticamente para ADMIN.
     */
    public boolean isMfaRequired() {
        return mfaRequired;
    }
}

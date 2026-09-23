package com.medicsalud.security;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Autenticación de Múltiples Factores (MFA / TOTP)
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * OWASP Top10: A07 – Identification and Authentication Failures
 * RFC 6238   : TOTP – Time-Based One-Time Password Algorithm
 *
 * Esta clase implementa TOTP directamente con la JDK estándar (HmacSHA1)
 * sin dependencias externas inseguras.  El secreto del usuario se almacena
 * cifrado en base de datos (ver EncryptedStringConverter).
 * ═══════════════════════════════════════════════════════════════════════
 */

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Utilidad TOTP (RFC 6238).
 *
 * <p>Genera códigos de 6 dígitos con paso de tiempo de 30 segundos y
 * HMAC-SHA1.  Permite una ventana de ±1 paso para compensar desfase
 * de reloj entre cliente y servidor.
 *
 * <p><b>Seguridad (ISO 27001 A.9.4.2 / OWASP MFA)</b>:
 * <ul>
 *   <li>El secreto se genera con {@link SecureRandom} (160 bits).</li>
 *   <li>El secreto se almacena cifrado en BD mediante AES-GCM
 *       ({@link EncryptedStringConverter}).</li>
 *   <li>La comparación de códigos es resistente a timing-attack.</li>
 * </ul>
 */
public final class TotpUtil {

    // ── Parámetros TOTP (RFC 6238) ────────────────────────────────────
    private static final int    CODE_DIGITS       = 6;
    private static final int    TIME_STEP_SECONDS = 30;
    private static final int    CLOCK_SKEW_STEPS  = 1;   // ±1 ventana
    private static final String HMAC_ALGORITHM    = "HmacSHA1";

    // ── Generación de secretos ────────────────────────────────────────
    // ISO 27001 A.10.1.1 – uso de SecureRandom para material criptográfico
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TotpUtil() { /* clase de utilidad, no instanciar */ }

    /**
     * Genera un secreto aleatorio de 160 bits codificado en Base32 (Google
     * Authenticator lo usa como semilla QR).
     *
     * @return secreto Base32 listo para asociar al usuario
     */
    public static String generateSecret() {
        byte[] buffer = new byte[20]; // 160 bits
        SECURE_RANDOM.nextBytes(buffer);
        // Codificamos en Base32 usando la tabla RFC 4648 pura
        return base32Encode(buffer);
    }

    /**
     * Valida un código TOTP de 6 dígitos ingresado por el usuario.
     *
     * <p><b>OWASP MFA / ISO 27001 A.9.4.2</b>: Se permite una ventana de
     * ±{@value CLOCK_SKEW_STEPS} pasos de tiempo para compensar desfase
     * de reloj sin comprometer la seguridad significativamente.
     *
     * @param base32Secret secreto Base32 del usuario (descifrado en memoria)
     * @param code         código de 6 dígitos ingresado por el usuario
     * @return {@code true} si el código es válido en la ventana actual
     */
    public static boolean validateCode(String base32Secret, String code) {
        if (base32Secret == null || code == null || code.length() != CODE_DIGITS) {
            return false;
        }
        try {
            long timeIndex = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
            for (int delta = -CLOCK_SKEW_STEPS; delta <= CLOCK_SKEW_STEPS; delta++) {
                String generated = generateCode(base32Secret, timeIndex + delta);
                // Comparación de tiempo constante para resistir timing attacks
                if (constantTimeEquals(generated, code)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // No revelar detalles del error (OWASP A07)
            return false;
        }
        return false;
    }

    // ── Métodos privados ──────────────────────────────────────────────

    private static String generateCode(String base32Secret, long timeIndex) throws Exception {
        byte[] key  = base32Decode(base32Secret);
        byte[] data = longToBytes(timeIndex);

        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset]     & 0x7F) << 24)
                   | ((hash[offset + 1] & 0xFF) << 16)
                   | ((hash[offset + 2] & 0xFF) <<  8)
                   |  (hash[offset + 3] & 0xFF);

        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", otp);
    }

    /** Convierte long a array de 8 bytes big-endian (RFC 6238). */
    private static byte[] longToBytes(long value) {
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return data;
    }

    /**
     * Comparación en tiempo constante para evitar timing attacks.
     * (OWASP: use constant-time comparison for security-sensitive strings)
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    // ── Base32 puro (RFC 4648) ────────────────────────────────────────
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String base32) {
        String s = base32.toUpperCase().replaceAll("[=\\s]", "");
        int outputLength = (s.length() * 5) / 8;
        byte[] result = new byte[outputLength];
        int buffer = 0, bitsLeft = 0, idx = 0;
        for (char c : s.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) throw new IllegalArgumentException("Carácter Base32 inválido: " + c);
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                result[idx++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return result;
    }
}

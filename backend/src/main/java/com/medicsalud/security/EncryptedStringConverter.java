package com.medicsalud.security;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Cifrado de datos sensibles en Base de Datos
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.10.1.1 – Política de uso de controles criptográficos
 * ISO 27001  : A.10.1.2 – Gestión de claves
 * OWASP Top10: A02 – Cryptographic Failures
 *
 * Convierte atributos sensibles (ej. secreto TOTP) a/desde columnas
 * cifradas en BD.  Utiliza AES-256 en modo GCM (Galois/Counter Mode),
 * que proporciona:
 *   • Confidencialidad  (cifrado por bloque)
 *   • Integridad/Autenticidad (etiqueta de autenticación GCM)
 *   • IV aleatorio por operación (evita ataques de reutilización de IV)
 *
 * La clave se inyecta desde la propiedad de entorno
 * ${security.encryption.key} para evitar secretos hardcodeados
 * (OWASP A02 / ISO 27001 A.10.1.2).
 * ═══════════════════════════════════════════════════════════════════════
 */

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * JPA converter que cifra/descifra campos {@code String} usando AES-256/GCM.
 *
 * <p><b>Esquema de almacenamiento en BD</b> (Base64):
 * <pre>[ 12 bytes IV ][ texto cifrado + 16 bytes GCM tag ]</pre>
 *
 * <p><b>ISO 27001 A.10.1.1 / OWASP A02</b>:
 * <ul>
 *   <li>AES-256 — longitud de clave ≥ 256 bits.</li>
 *   <li>GCM     — modo autenticado; detecta manipulación.</li>
 *   <li>IV único por cifrado — generado con {@link SecureRandom}.</li>
 *   <li>Clave inyectada por entorno, NO hardcodeada en código.</li>
 * </ul>
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    // ── Parámetros criptográficos ─────────────────────────────────────
    // ISO 27001 A.10.1.1 – AES-256-GCM (NIST recomendado)
    private static final String CIPHER_ALGORITHM    = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM       = "AES";
    private static final int    GCM_IV_LENGTH_BYTES = 12;   // 96-bit IV (NIST SP 800-38D)
    private static final int    GCM_TAG_LENGTH_BITS = 128;  // 128-bit authentication tag

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Clave inyectada desde propiedad de entorno ────────────────────
    // ISO 27001 A.10.1.2 – Gestión de claves: nunca en código fuente
    private final SecretKeySpec secretKey;

    public EncryptedStringConverter(
            @Value("${security.encryption.key}") String rawKey) {
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        // Aseguramos exactamente 32 bytes (AES-256)
        byte[] key32 = Arrays.copyOf(keyBytes, 32);
        this.secretKey = new SecretKeySpec(key32, KEY_ALGORITHM);
    }

    /**
     * Cifra el atributo Java antes de persistirlo en la BD.
     *
     * <p>Genera un IV aleatorio de 12 bytes y lo antepone al ciphertext
     * para que pueda recuperarse durante el descifrado.
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        try {
            // IV único por cifrado (OWASP A02 / NIST SP 800-38D)
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // Formato almacenado: Base64( IV ‖ ciphertext+tag )
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("[SECURITY] Error al cifrar atributo en BD (AES-GCM)", e);
        }
    }

    /**
     * Descifra el valor leído desde BD para exponerlo como atributo Java.
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);

            // Recuperar IV de los primeros 12 bytes
            byte[] iv         = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("[SECURITY] Error al descifrar atributo de BD (AES-GCM)", e);
        }
    }
}

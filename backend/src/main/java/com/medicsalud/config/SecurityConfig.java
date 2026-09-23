package com.medicsalud.config;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Configuración Central de Spring Security
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.1.2  – Acceso a redes y servicios de red (RBAC)
 * ISO 27001  : A.9.4.1  – Restricción del acceso a la información
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * ISO 27001  : A.14.1.2 – Securización de servicios de aplicación en redes
 * OWASP Top10: A01 – Broken Access Control
 * OWASP Top10: A05 – Security Misconfiguration
 * OWASP Top10: A07 – Identification and Authentication Failures
 *
 * Centraliza:
 *  • Política de sesiones  → STATELESS (JWT, sin cookies de sesión)
 *  • CORS                  → Controlado por lista de orígenes permitidos
 *  • CSRF                  → Desactivado (API REST stateless con JWT)
 *  • Reglas de autorización → RBAC por endpoint
 *  • Pipeline de filtros   → JWT Auth → MFA → Rate Limit → Authorization
 * ═══════════════════════════════════════════════════════════════════════
 */

import com.medicsalud.security.JwtAuthenticationFilter;
import com.medicsalud.security.JwtAuthorizationFilter;
import com.medicsalud.security.MfaFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración de seguridad de Spring Security.
 *
 * <p><b>Decisiones de diseño (ISO 27001 / OWASP)</b>:
 *
 * <ol>
 *   <li><b>STATELESS (A.9.4.2 / OWASP A07)</b>: No se crean sesiones HTTP;
 *       cada petición se autentica independientemente con el token JWT.</li>
 *   <li><b>CSRF desactivado (OWASP A05)</b>: Correcto para APIs REST con
 *       tokens Bearer; los tokens CSRF solo tienen sentido en sesiones basadas
 *       en cookies.  El frontend envía JWT en el encabezado {@code Authorization}.</li>
 *   <li><b>BCrypt (ISO A.9.4.2 / OWASP A02)</b>: Factor de coste adaptativo
 *       para proteger contraseñas almacenadas en BD.</li>
 *   <li><b>RBAC (ISO A.9.1.2 / OWASP A01)</b>: Cada endpoint declara el rol
 *       mínimo requerido; denegación por defecto con {@code anyRequest().authenticated()}.</li>
 *   <li><b>CORS (ISO A.14.1.2)</b>: Solo permite métodos y cabeceras necesarios;
 *       en producción reemplazar "*" por la lista de dominios permitidos.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Habilita @PreAuthorize / @PostAuthorize en controladores
public class SecurityConfig {

    /**
     * Cadena de filtros de seguridad principal.
     *
     * <p><b>Orden del pipeline de filtros</b> (ISO 27001 A.9.4.2):
     * <pre>
     * JwtAuthorizationFilter (cabeceras HTTP)
     *   → JwtAuthenticationFilter (valida Bearer token)
     *     → MfaFilter (verifica claim mfaVerified en rutas privilegiadas)
     *       → RateLimitingFilter (Bucket4j – ver RateLimitingFilter.java)
     *         → Spring Security authorization rules
     * </pre>
     *
     * @param http                     configurador HTTP de Spring Security
     * @param jwtAuthenticationFilter  extrae y valida el JWT de cada petición
     * @param jwtAuthorizationFilter   agrega cabeceras de seguridad a la respuesta
     * @param mfaFilter                exige MFA verificado en rutas de admin
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity           http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthorizationFilter  jwtAuthorizationFilter,
            MfaFilter               mfaFilter) throws Exception {

        http
            // ── Política de sesiones: STATELESS (OWASP A07 / ISO A.9.4.2) ──
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── CSRF desactivado para API REST stateless (OWASP A05) ─────────
            .csrf(csrf -> csrf.disable())

            // ── CORS (ISO A.14.1.2) ──────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Pipeline de filtros personalizados ────────────────────────────
            // Orden: Authorization headers → JWT Auth → MFA check
            .addFilterBefore(jwtAuthorizationFilter,  UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(mfaFilter, JwtAuthenticationFilter.class)

            // ── Reglas de acceso RBAC (ISO A.9.1.2 / OWASP A01) ─────────────
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos: login, registro, refresco de token, salud
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/sms/request",
                        "/api/v1/auth/sms/verify",
                        "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/services", "/api/v1/doctors/available").permitAll()

                // H2 console — solo en desarrollo (desactivar en producción)
                // Se controla desde application.properties, pero en prod no se expone el endpoint
                .requestMatchers("/h2-console/**").permitAll()

                // Dashboard y admin: solo personal autorizado
                // ISO 27001 A.9.4.1 – restricción de acceso a la información
                .requestMatchers("/api/v1/dashboard/**").hasAnyRole("ADMIN", "PERSONAL")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Checkout: requiere autenticación (cualquier rol autenticado)
                .requestMatchers(HttpMethod.POST, "/api/v1/checkout").authenticated()

                // Todo lo demás requiere autenticación (denegación por defecto)
                .anyRequest().authenticated()
            );

        // Necesario para que H2 console funcione en frames (solo desarrollo)
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * AuthenticationManager expuesto como bean para uso en AuthService.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Encoder de contraseñas BCrypt.
     *
     * <p><b>ISO 27001 A.9.4.2 / OWASP A02 (Cryptographic Failures)</b>:
     * BCrypt es adaptive hashing; el factor de coste predeterminado (10)
     * es adecuado para la mayoría de entornos.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();  // Coste 10 por defecto
    }

    /**
     * Configuración CORS.
     *
     * <p><b>ISO 27001 A.14.1.2</b>: En producción, reemplazar
     * {@code allowedOriginPatterns("*")} por la lista explícita de dominios
     * permitidos (ej. {@code https://medicsalud.com}).
     *
     * <p><b>ADVERTENCIA</b>: El patrón {@code "*"} está permitido solo para
     * desarrollo local.  En producción limitar a dominios específicos.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // TODO producción: reemplazar por dominios específicos
        // ISO 27001 A.14.1.2 – solo orígenes autorizados
        // Leemos desde el ambiente o usamos localhost en local
        String allowedOrigins = System.getenv("ALLOWED_ORIGINS");
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            configuration.setAllowedOriginPatterns(List.of(
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:5174",
                    "http://127.0.0.1:5174",
                    "http://localhost:5175",
                    "http://127.0.0.1:5175"
            ));
        } else {
            configuration.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Pre-flight cache 1 hora

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

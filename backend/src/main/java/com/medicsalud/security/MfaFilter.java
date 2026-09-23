package com.medicsalud.security;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Filtro de Verificación MFA en endpoints privilegiados
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * ISO 27001  : A.9.3.1  – Uso de información de autenticación secreta
 * OWASP Top10: A07 – Identification and Authentication Failures
 *
 * Verifica que los tokens JWT usados para acceder al dashboard/admin
 * incluyan el claim "mfaVerified=true", que el AuthService establece
 * solo cuando el usuario pasó exitosamente el segundo factor TOTP.
 * ═══════════════════════════════════════════════════════════════════════
 */

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

/**
 * Filtro que exige MFA verificado para rutas de administración/dashboard.
 *
 * <p><b>ISO 27001 A.9.4.2 / OWASP A07</b>:
 * <ul>
 *   <li>Rutas protegidas: {@code /api/v1/dashboard/**}, {@code /api/v1/admin/**}.</li>
 *   <li>Si el claim {@code mfaVerified} no es {@code true}, se rechaza con HTTP 403.</li>
 *   <li>Esto impide que un token válido sin MFA acceda a recursos privilegiados.</li>
 * </ul>
 */
@Component
public class MfaFilter extends OncePerRequestFilter {

    // Rutas que exigen segundo factor verificado
    private static final List<String> MFA_REQUIRED_PATHS = List.of(
            "/api/v1/dashboard",
            "/api/v1/admin"
    );

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Solo aplica a rutas privilegiadas
        return MFA_REQUIRED_PATHS.stream().noneMatch(uri::startsWith);
    }

    /**
     * Verifica el claim {@code mfaVerified} en el token JWT.
     *
     * <p>Si el claim está ausente o es {@code false}, responde HTTP 403
     * con JSON de error sin revelar detalles internos (OWASP A07).
     */
    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Sin token: Spring Security lo manejará con 401
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(authHeader.substring(7))
                    .getBody();

            Boolean mfaVerified = claims.get("mfaVerified", Boolean.class);

            if (!Boolean.TRUE.equals(mfaVerified)) {
                // ISO 27001 A.9.4.2 – exige segundo factor para acceso privilegiado
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"error\":\"Se requiere verificación MFA para acceder a este recurso\"}");
                return;
            }
        } catch (Exception e) {
            // Token inválido/malformado – dejar que JwtAuthenticationFilter maneje 401
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}

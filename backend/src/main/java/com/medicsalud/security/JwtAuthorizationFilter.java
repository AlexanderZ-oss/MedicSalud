package com.medicsalud.security;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Filtro de Autorización JWT (stub delegado)
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.1.2  – Acceso a redes y servicios de red
 * OWASP Top10: A01 – Broken Access Control
 *
 * La autorización real por roles se delega a Spring Security mediante
 * SecurityConfig + @PreAuthorize.  Este filtro existe como punto de
 * extensión para lógica adicional de autorización a nivel de filtro
 * (ej. auditoría de acceso, cabeceras de respuesta).
 * ═══════════════════════════════════════════════════════════════════════
 */

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de autorización complementario al pipeline de Spring Security.
 *
 * <p><b>ISO 27001 A.9.1.2 / OWASP A01</b>: Agrega cabeceras de seguridad
 * HTTP en cada respuesta para reforzar la política de acceso:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — previene MIME sniffing.</li>
 *   <li>{@code X-Frame-Options: DENY} — previene clickjacking.</li>
 *   <li>{@code Referrer-Policy: no-referrer} — protege datos de referencia.</li>
 * </ul>
 */
@Component
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthorizationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain) throws ServletException, IOException {

        // ── Cabeceras de seguridad HTTP (OWASP A01 / ISO 27001 A.9.1.2) ─
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        filterChain.doFilter(request, response);
    }
}

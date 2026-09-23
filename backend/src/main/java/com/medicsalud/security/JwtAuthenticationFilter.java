package com.medicsalud.security;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Filtro de Autenticación JWT (Bearer Token)
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * ISO 27001  : A.9.1.2  – Acceso a redes y servicios de red
 * OWASP Top10: A07 – Identification and Authentication Failures
 *
 * Intercepta cada petición HTTP, extrae el token Bearer del encabezado
 * Authorization y, si es válido, establece el contexto de seguridad de
 * Spring Security con los datos del usuario autenticado.
 * ═══════════════════════════════════════════════════════════════════════
 */

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro JWT que se ejecuta una vez por petición HTTP.
 *
 * <p><b>Flujo de autenticación (ISO 27001 A.9.4.2 / OWASP A07)</b>:
 * <ol>
 *   <li>Extrae el token JWT del encabezado {@code Authorization: Bearer <token>}.</li>
 *   <li>Valida firma y expiración del token ({@link JwtTokenProvider#validateAccessToken}).</li>
 *   <li>Carga los detalles del usuario desde BD ({@link CustomUserDetailsService}).</li>
 *   <li>Establece el contexto de seguridad de Spring para la petición actual.</li>
 * </ol>
 *
 * <p>Si el token es inválido o ausente, la petición continúa sin autenticar;
 * Spring Security rechazará el acceso a recursos protegidos posteriormente.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // ── Dependencias ──────────────────────────────────────────────────
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain) throws ServletException, IOException {

        // 1. Extraer encabezado Authorization
        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            // 2. Obtener token sin el prefijo "Bearer "
            String token = authHeader.substring(7);

            // 3. Validar token (ISO 27001 A.9.4.2 – autenticación segura)
            if (jwtTokenProvider.validateAccessToken(token)) {

                String username = jwtTokenProvider.getUsernameFromToken(token);
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

                // 4. Establecer contexto de seguridad (OWASP A07)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // Si el token es inválido, NO se lanza excepción aquí;
            // Spring Security denegará el acceso al recurso protegido.
        }

        filterChain.doFilter(request, response);
    }
}

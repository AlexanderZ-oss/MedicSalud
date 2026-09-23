package com.medicsalud.controller;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Controlador del Panel de Administración
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.1.2  – Acceso a redes y servicios de red (RBAC)
 * ISO 27001  : A.9.4.1  – Restricción del acceso a la información
 * OWASP Top10: A01 – Broken Access Control
 *
 * Endpoints del panel interno de MedicSalud.  El acceso está restringido
 * a roles ADMIN y PERSONAL mediante @PreAuthorize y SecurityConfig.
 * Adicionalmente, el MfaFilter exige que el token JWT tenga el claim
 * mfaVerified=true para acceder a estas rutas.
 * ═══════════════════════════════════════════════════════════════════════
 */

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controlador del panel interno para personal autorizado.
 *
 * <p><b>Control de acceso (ISO 27001 A.9.1.2 / OWASP A01)</b>:
 * <ul>
 *   <li>Todos los endpoints requieren rol {@code ADMIN} o {@code PERSONAL}.</li>
 *   <li>{@code /api/v1/admin/**} está restringido solo a {@code ADMIN}.</li>
 *   <li>El {@link com.medicsalud.security.MfaFilter} valida el claim
 *       {@code mfaVerified=true} antes de llegar a este controlador.</li>
 *   <li>El usuario autenticado se inyecta desde el contexto de Spring Security
 *       (no desde parámetros de la petición — OWASP A01).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    // ── Panel del personal ────────────────────────────────────────────

    /**
     * Resumen del dashboard para personal autorizado.
     *
     * <p><b>ISO 27001 A.9.4.1</b>: Solo devuelve datos relevantes para el
     * rol del usuario; la identidad se obtiene del contexto de seguridad
     * (no del cuerpo de la petición).
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'PERSONAL')") // ISO A.9.1.2 / OWASP A01
    public ResponseEntity<?> dashboard(@AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "desconocido";

        return ResponseEntity.ok(Map.of(
                "status",    "ok",
                "usuario",   username,
                "timestamp", LocalDateTime.now().toString(),
                "stats", Map.of(
                        "pedidosHoy",          128,
                        "productosActivos",    24,
                        "revisionesPendientes", 6
                ),
                "inventario", List.of(
                        Map.of("nombre", "Paracetamol 500mg",   "unidades", 80),
                        Map.of("nombre", "Suero oral",          "unidades", 32),
                        Map.of("nombre", "Vitamina C + zinc",   "unidades", 55),
                        Map.of("nombre", "Ibuprofeno 400mg",    "unidades", 47),
                        Map.of("nombre", "Amoxicilina 500mg",   "unidades", 18)
                )
        ));
    }

    // ── Administración (solo ADMIN) ───────────────────────────────────

    /**
     * Panel de administración completo.
     *
     * <p><b>ISO 27001 A.9.4.1 / OWASP A01</b>: Acceso restringido
     * exclusivamente a rol {@code ADMIN}.
     */
    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')") // ISO A.9.1.2 – solo ADMIN
    public ResponseEntity<?> adminDashboard(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of(
                "status",    "admin",
                "usuario",   userDetails != null ? userDetails.getUsername() : "admin",
                "timestamp", LocalDateTime.now().toString(),
                "message",   "Panel de administración – acceso exclusivo ADMIN"
        ));
    }

}

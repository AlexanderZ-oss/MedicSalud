package com.medicsalud.controller;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Controlador de Administración (solo ROLE_ADMIN)
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.1.2  – Acceso a redes y servicios de red (RBAC)
 * ISO 27001  : A.9.2.1  – Registro y cancelación de usuarios
 * OWASP Top10: A01 – Broken Access Control
 * OWASP Top10: A03 – Injection (DTO para evitar exposición de entidades)
 *
 * Todos los endpoints requieren ROLE_ADMIN.
 * Se usa UserResponseDto para nunca exponer campos sensibles (password,
 * mfaSecret) en la respuesta REST — OWASP A03 / ISO A.9.3.1.
 * ═══════════════════════════════════════════════════════════════════════
 */

import com.medicsalud.model.User;
import com.medicsalud.repository.UserRepository;
import com.medicsalud.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador de administración — acceso exclusivo para ROLE_ADMIN.
 *
 * <p><b>ISO 27001 A.9.2.1 / OWASP A01</b>: Solo el administrador puede
 * listar y gestionar usuarios del sistema.
 *
 * <p><b>OWASP A03 / ISO A.9.3.1</b>: Se usa {@link UserResponseDto}
 * para evitar exponer campos sensibles ({@code password}, {@code mfaSecret})
 * directamente desde la entidad JPA.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')") // ISO A.9.1.2 — acceso exclusivo ADMIN
public class AdminController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthService authService;

    // ── Gestión de Usuarios ───────────────────────────────────────────

    /**
     * Lista todos los usuarios del sistema.
     *
     * <p><b>ISO 27001 A.9.2.1 / OWASP A03</b>: Se retorna un DTO que
     * excluye {@code password} y {@code mfaSecret} — nunca se exponen
     * datos criptográficos en la API.
     */
    @GetMapping("/users")
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponseDto::fromUser)
                .toList();
    }

    @PostMapping("/users/staff")
    public ResponseEntity<?> createStaff(@RequestBody Map<String, String> body) {
        try {
            User user = authService.registerStaff(body.get("username"), body.get("password"),
                    body.getOrDefault("role", "ROLE_PERSONAL"), body.get("phoneNumber"), body.get("email"));
            return ResponseEntity.ok(UserResponseDto.fromUser(user));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    // ── Productos / Inventario ────────────────────────────────────────

    /**
     * Lista el inventario de productos.
     *
     * <p>Datos mock hasta integración con entidad Product.
     * TODO: crear entidad Product + ProductRepository y reemplazar.
     */
    @GetMapping("/products")
    public List<Map<String, Object>> getProducts() {
        return List.of(
            Map.of("nombre", "Paracetamol 500mg",  "unidades", 80, "estado", "ok"),
            Map.of("nombre", "Suero oral",          "unidades", 32, "estado", "ok"),
            Map.of("nombre", "Vitamina C + zinc",   "unidades", 55, "estado", "ok"),
            Map.of("nombre", "Ibuprofeno 400mg",    "unidades", 47, "estado", "ok"),
            Map.of("nombre", "Amoxicilina 500mg",   "unidades", 18, "estado", "low")
        );
    }

    // ── DTO de respuesta ──────────────────────────────────────────────

    /**
     * DTO seguro para exponer datos de usuario.
     *
     * <p><b>ISO 27001 A.9.3.1 / OWASP A03</b>: Nunca se incluyen
     * {@code password} ni {@code mfaSecret} en la respuesta al cliente.
     */
    public record UserResponseDto(
            Long id,
            String username,
            String role,
            boolean mfaRequired
    ) {
        /**
         * Construye el DTO desde la entidad User.
         * Garantiza que los campos sensibles nunca salgan al exterior.
         */
        public static UserResponseDto fromUser(User user) {
            return new UserResponseDto(
                    user.getId(),
                    user.getUsername(),
                    user.getRole(),
                    user.isMfaRequired()
            );
        }
    }
}

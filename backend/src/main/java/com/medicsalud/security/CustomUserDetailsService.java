package com.medicsalud.security;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Servicio de carga de detalles de usuario
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.2.1  – Registro y cancelación de usuarios
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * OWASP Top10: A07 – Identification and Authentication Failures
 *
 * Implementa UserDetailsService de Spring Security para cargar usuarios
 * desde BD y exponerlos como objetos UserDetails con sus roles (RBAC).
 * ═══════════════════════════════════════════════════════════════════════
 */

import com.medicsalud.model.User;
import com.medicsalud.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Carga los detalles del usuario desde la base de datos.
 *
 * <p><b>ISO 27001 A.9.2.1 / OWASP A07</b>:
 * <ul>
 *   <li>El rol almacenado en BD se normaliza al prefijo {@code ROLE_} que
 *       Spring Security requiere para {@code hasRole()} / {@code @PreAuthorize}.</li>
 *   <li>Si el usuario no existe se lanza {@link UsernameNotFoundException};
 *       Spring Security responderá con HTTP 401 sin revelar si el usuario
 *       existe o no (resistencia a enumeración de usuarios – OWASP A07).</li>
 *   <li>La contraseña almacenada es BCrypt; Spring Security la compara
 *       automáticamente durante la autenticación ({@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}).</li>
 * </ul>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Carga el usuario por nombre de usuario para autenticación.
     *
     * <p><b>ISO 27001 A.9.4.2</b>: Los roles se mapean a authorities de
     * Spring Security para control de acceso basado en roles (RBAC).
     *
     * @param username nombre de usuario (case-sensitive)
     * @throws UsernameNotFoundException si el usuario no existe en BD
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + username)); // Mensaje interno (no expuesto al cliente)

        // Normalizar rol al formato ROLE_XXX que Spring Security requiere
        String role = user.getRole();
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role.toUpperCase();
        }

        // Construir UserDetails con contraseña BCrypt y autoridades RBAC
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())           // BCrypt (A.9.4.2)
                .authorities(List.of(new SimpleGrantedAuthority(role))) // RBAC (A.9.1.2)
                .build();
    }
}

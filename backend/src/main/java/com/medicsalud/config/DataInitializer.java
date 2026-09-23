package com.medicsalud.config;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * DATOS DE PRUEBA – Inicialización de Usuarios del Sistema
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.2.1  – Registro y cancelación de usuarios
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 *
 * Solo para desarrollo/pruebas. En producción deshabilitar con el
 * perfil spring.profiles.active=prod o eliminando este bean.
 *
 * Usuarios creados al arrancar (si no existen):
 *   • admin    / Admin@2024!   → ROLE_ADMIN    (mfaRequired=false para pruebas)
 *   • personal / Personal@2024 → ROLE_PERSONAL (mfaRequired=false)
 *   • cliente  / Cliente@2024  → ROLE_CLIENT   (mfaRequired=false)
 * ═══════════════════════════════════════════════════════════════════════
 */

import com.medicsalud.model.User;
import com.medicsalud.repository.UserRepository;
import com.medicsalud.repository.ProductRepository;
import com.medicsalud.repository.DoctorRepository;
import com.medicsalud.repository.MedicalServiceRepository;
import com.medicsalud.model.Product;
import com.medicsalud.model.Doctor;
import com.medicsalud.model.MedicalService;
import com.medicsalud.security.TotpUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

/**
 * Inicializador de datos de prueba.
 *
 * <p><b>ISO 27001 A.9.2.1</b>: Los usuarios de prueba se crean con
 * credenciales conocidas y roles predefinidos para facilitar la validación
 * del sistema durante el ciclo de desarrollo.
 *
 * <p><b>ADVERTENCIA</b>: Este bean NO debe activarse en producción.
 * Asegúrese de usar el perfil {@code prod} o de eliminar esta clase
 * antes del despliegue.
 */
@Configuration
public class DataInitializer {

    /**
     * Seed de usuarios de prueba al arrancar la aplicación.
     *
     * <p>Crea los tres roles del sistema para permitir pruebas completas:
     * <ul>
     *   <li>{@code admin} → ROLE_ADMIN (acceso completo al panel + admin)</li>
     *   <li>{@code personal} → ROLE_PERSONAL (acceso al panel, sin admin)</li>
     *   <li>{@code cliente} → ROLE_CLIENT (solo checkout / tienda)</li>
     * </ul>
     *
     * <p>MFA está desactivado para todos los usuarios de prueba
     * ({@code mfaRequired=false}) para facilitar las pruebas automatizadas.
     */
    @Bean
    public CommandLineRunner seedUsers(UserRepository userRepository,
                                      PasswordEncoder passwordEncoder,
                                      ProductRepository productRepository,
                                      DoctorRepository doctorRepository,
                                      MedicalServiceRepository serviceRepository) {
        return args -> {
            // ── Admin ────────────────────────────────────────────────────
            // ISO A.9.2.1: usuario privilegiado creado de forma controlada
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setEmail("admin@medicsalud.local");
                admin.setPassword(passwordEncoder.encode("Admin@2024!"));
                admin.setRole("ROLE_ADMIN");
                admin.setMfaRequired(false); // false para pruebas — habilitar en producción
                admin.setMfaSecret(TotpUtil.generateSecret()); // secreto TOTP cifrado (AES-GCM)
                admin.setPhoneNumber("+15550000001");
                userRepository.save(admin);
                System.out.println("[DataInitializer] ✅ Usuario 'admin' creado (ROLE_ADMIN)");
            }

            // ── Personal ─────────────────────────────────────────────────
            if (userRepository.findByUsername("personal").isEmpty()) {
                User personal = new User();
                personal.setUsername("personal");
                personal.setEmail("personal@medicsalud.local");
                personal.setPassword(passwordEncoder.encode("Personal@2024"));
                personal.setRole("ROLE_PERSONAL");
                personal.setMfaRequired(false);
                personal.setMfaSecret(TotpUtil.generateSecret());
                personal.setPhoneNumber("+15550000002");
                userRepository.save(personal);
                System.out.println("[DataInitializer] ✅ Usuario 'personal' creado (ROLE_PERSONAL)");
            }

            // ── Cliente ───────────────────────────────────────────────────
            if (userRepository.findByUsername("cliente").isEmpty()) {
                User cliente = new User();
                cliente.setUsername("cliente");
                cliente.setEmail("cliente@medicsalud.local");
                cliente.setPassword(passwordEncoder.encode("Cliente@2024"));
                cliente.setRole("ROLE_CLIENT");
                cliente.setMfaRequired(false);
                cliente.setMfaSecret(TotpUtil.generateSecret());
                userRepository.save(cliente);
                System.out.println("[DataInitializer] ✅ Usuario 'cliente' creado (ROLE_CLIENT)");
            }

            System.out.println("[DataInitializer] 🔐 Usuarios de prueba listos para el sistema.");
            System.out.println("[DataInitializer] ┌──────────────┬──────────────────┬───────────────┐");
            System.out.println("[DataInitializer] │ Usuario      │ Contraseña       │ Rol           │");
            System.out.println("[DataInitializer] ├──────────────┼──────────────────┼───────────────┤");
            System.out.println("[DataInitializer] │ admin        │ Admin@2024!      │ ROLE_ADMIN    │");
            System.out.println("[DataInitializer] │ personal     │ Personal@2024    │ ROLE_PERSONAL │");
            System.out.println("[DataInitializer] │ cliente      │ Cliente@2024     │ ROLE_CLIENT   │");
            System.out.println("[DataInitializer] └──────────────┴──────────────────┴───────────────┘");

            if (productRepository.count() == 0) {
                productRepository.save(new Product(null, "Paracetamol 500mg", new BigDecimal("3.50"), 80, false, "Medicamentos", "LOT-PA-01", null, "FarmaVida"));
                productRepository.save(new Product(null, "Amoxicilina 500mg", new BigDecimal("8.90"), 18, true, "Medicamentos", "LOT-AM-01", null, "FarmaVida"));
                productRepository.save(new Product(null, "Vitamina C + zinc", new BigDecimal("8.90"), 55, false, "Vitaminas", "LOT-VZ-01", null, "SaludAndina"));
            }
            if (doctorRepository.count() == 0) {
                doctorRepository.save(new Doctor(null, "Dra. Ana Torres", "Medicina general", "ana.torres@medicsalud.local", true));
                doctorRepository.save(new Doctor(null, "Dr. Luis Mendoza", "Medicina familiar", "luis.mendoza@medicsalud.local", true));
            }
            if (serviceRepository.count() == 0) {
                serviceRepository.save(new MedicalService(null, "Consulta general", "Evaluación médica y orientación inicial.", new BigDecimal("45.00"), 30, true));
                serviceRepository.save(new MedicalService(null, "Control preventivo", "Revisión preventiva y seguimiento de salud.", new BigDecimal("35.00"), 30, true));
            }
        };
    }
}

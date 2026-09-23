package com.medicsalud.controller;

import com.medicsalud.model.Appointment;
import com.medicsalud.model.Doctor;
import com.medicsalud.model.MedicalService;
import com.medicsalud.model.Product;
import com.medicsalud.repository.AppointmentRepository;
import com.medicsalud.repository.DoctorRepository;
import com.medicsalud.repository.MedicalServiceRepository;
import com.medicsalud.repository.ProductRepository;
import com.medicsalud.service.OperationsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/operations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationsController {
    private final ProductRepository productRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalServiceRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final OperationsService operationsService;

    public AdminOperationsController(ProductRepository productRepository, DoctorRepository doctorRepository,
                                      MedicalServiceRepository serviceRepository, AppointmentRepository appointmentRepository,
                                      OperationsService operationsService) {
        this.productRepository = productRepository;
        this.doctorRepository = doctorRepository;
        this.serviceRepository = serviceRepository;
        this.appointmentRepository = appointmentRepository;
        this.operationsService = operationsService;
    }

    @GetMapping("/products") public Object products() { return productRepository.findAll(); }
    @PostMapping("/products") public Product createProduct(@RequestBody Product product) { product.setId(null); return productRepository.save(product); }
    @PutMapping("/products/{id}") public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Product input) {
        return productRepository.findById(id).map(product -> {
            product.setName(input.getName()); product.setPrice(input.getPrice()); product.setStock(input.getStock());
            product.setPrescriptionRequired(input.isPrescriptionRequired()); product.setCategory(input.getCategory());
            product.setBatchNumber(input.getBatchNumber()); product.setExpirationDate(input.getExpirationDate()); product.setSupplier(input.getSupplier());
            return ResponseEntity.ok(productRepository.save(product));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/doctors") public Object doctors() { return doctorRepository.findAll(); }
    @PostMapping("/doctors") public Doctor createDoctor(@RequestBody Doctor doctor) { doctor.setId(null); return doctorRepository.save(doctor); }
    @PutMapping("/doctors/{id}/availability") public ResponseEntity<?> availability(@PathVariable Long id, @RequestParam boolean available) {
        return doctorRepository.findById(id).map(doctor -> { doctor.setAvailable(available); return ResponseEntity.ok(doctorRepository.save(doctor)); }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/services") public Object allServices() { return serviceRepository.findAll(); }
    @PostMapping("/services") public MedicalService createService(@RequestBody MedicalService service) { service.setId(null); return serviceRepository.save(service); }
    @PutMapping("/services/{id}") public ResponseEntity<?> updateService(@PathVariable Long id, @RequestBody MedicalService input) {
        return serviceRepository.findById(id).map(service -> { service.setName(input.getName()); service.setDescription(input.getDescription()); service.setPrice(input.getPrice()); service.setDurationMinutes(input.getDurationMinutes()); service.setActive(input.isActive()); return ResponseEntity.ok(serviceRepository.save(service)); }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/appointments") public Object appointments() { return appointmentRepository.findAll().stream().map(OperationsController.AppointmentResponse::from).toList(); }
    @PutMapping("/appointments/{id}/status") public ResponseEntity<?> appointmentStatus(@PathVariable Long id, @RequestParam Appointment.Status status) {
        return appointmentRepository.findById(id).map(appointment -> { appointment.setStatus(status); appointment.setNotificationMessage("Actualización de cita: " + status.name()); return ResponseEntity.ok(appointmentRepository.save(appointment)); }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/appointments/{id}/doctor")
    public ResponseEntity<?> reassignDoctor(@PathVariable Long id, @RequestParam Long doctorId) {
        return appointmentRepository.findById(id).flatMap(appointment -> doctorRepository.findById(doctorId).map(doctor -> {
            appointment.setDoctor(doctor);
            appointment.setStatus(Appointment.Status.RESCHEDULED);
            appointment.setNotificationMessage("Nueva asignación médica: " + doctor.getFullName());
            return ResponseEntity.ok(appointmentRepository.save(appointment));
        })).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> reportSummary() {
        return Map.of("products", productRepository.count(), "lowStock", operationsService.lowStockCount(),
                "doctors", doctorRepository.count(), "services", serviceRepository.count(),
                "appointments", appointmentRepository.count(), "generatedAt", LocalDateTime.now().toString());
    }
}

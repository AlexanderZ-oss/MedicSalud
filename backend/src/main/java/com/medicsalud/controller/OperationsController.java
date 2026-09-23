package com.medicsalud.controller;

import com.medicsalud.model.Appointment;
import com.medicsalud.model.Invoice;
import com.medicsalud.model.Product;
import com.medicsalud.repository.DoctorRepository;
import com.medicsalud.repository.InvoiceRepository;
import com.medicsalud.repository.MedicalServiceRepository;
import com.medicsalud.repository.ProductRepository;
import com.medicsalud.service.OperationsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class OperationsController {
    private final MedicalServiceRepository serviceRepository;
    private final DoctorRepository doctorRepository;
    private final OperationsService operationsService;
    private final ProductRepository productRepository;
    private final InvoiceRepository invoiceRepository;

    public OperationsController(MedicalServiceRepository serviceRepository,
                                DoctorRepository doctorRepository,
                                OperationsService operationsService,
                                ProductRepository productRepository,
                                InvoiceRepository invoiceRepository) {
        this.serviceRepository = serviceRepository;
        this.doctorRepository = doctorRepository;
        this.operationsService = operationsService;
        this.productRepository = productRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @GetMapping("/services")
    public Object services() {
        return serviceRepository.findAll().stream().filter(service -> service.isActive()).toList();
    }

    @GetMapping("/doctors/available")
    public Object availableDoctors() {
        return doctorRepository.findByAvailableTrue();
    }

    @PostMapping("/appointments")
    public ResponseEntity<?> schedule(@RequestBody Map<String, Object> body) {
        try {
            Appointment appointment = operationsService.scheduleAppointment(
                    (String) body.get("patientName"),
                    (String) body.get("patientDni"),
                    (String) body.get("symptoms"),
                    Integer.parseInt(String.valueOf(body.get("patientAge"))),
                    Long.parseLong(String.valueOf(body.get("serviceId"))),
                    body.get("date") == null ? null : LocalDate.parse((String) body.get("date")));
            return ResponseEntity.ok(AppointmentResponse.from(appointment));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/appointments")
    public Object appointments(@RequestParam String dni) {
        return operationsService.appointmentsForDni(dni).stream().map(AppointmentResponse::from).toList();
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        try {
            String patientName = (String) body.get("patientName");
            String patientDni = (String) body.get("patientDni");
            String deliveryMode = String.valueOf(body.getOrDefault("deliveryMode", "PICKUP"));
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (patientName == null || patientName.isBlank() || patientDni == null || patientDni.isBlank() || items == null || items.isEmpty()) {
                throw new IllegalArgumentException("Datos de compra incompletos");
            }
            BigDecimal total = BigDecimal.ZERO;
            boolean prescriptionRequired = false;
            for (Map<String, Object> item : items) {
                Product product = productRepository.findById(Long.parseLong(String.valueOf(item.get("productId"))))
                        .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
                int quantity = Integer.parseInt(String.valueOf(item.get("quantity")));
                if (quantity < 1 || product.getStock() < quantity) throw new IllegalArgumentException("Stock insuficiente");
                product.setStock(product.getStock() - quantity);
                productRepository.save(product);
                total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
                prescriptionRequired |= product.isPrescriptionRequired();
            }
            if (prescriptionRequired && !"PICKUP_PRESCRIPTION".equals(deliveryMode)) {
                throw new IllegalArgumentException("Los medicamentos con receta solo se entregan presencialmente");
            }
            Invoice invoice = invoiceRepository.save(new Invoice(null, patientName.trim(), patientDni.trim(), total,
                    prescriptionRequired ? "PENDING_PRESCRIPTION" : "PENDING", deliveryMode,
                    prescriptionRequired, LocalDateTime.now()));
            return ResponseEntity.ok(Map.of("invoiceId", invoice.getId(), "total", total,
                    "status", invoice.getStatus(), "prescriptionRequired", prescriptionRequired));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/invoices")
    public Object invoices(@RequestParam String dni) {
        return invoiceRepository.findByPatientDniOrderByCreatedAtDesc(dni.trim());
    }

    public record AppointmentResponse(Long id, String patientName, String patientDni, String symptoms,
                                      int patientAge, String service, String doctor,
                                      String scheduledAt, String status, String notificationMessage) {
        static AppointmentResponse from(Appointment appointment) {
            return new AppointmentResponse(appointment.getId(), appointment.getPatientName(), appointment.getPatientDni(),
                    appointment.getSymptoms(), appointment.getPatientAge(), appointment.getService().getName(),
                    appointment.getDoctor().getFullName(), appointment.getScheduledAt().toString(),
                    appointment.getStatus().name(), appointment.getNotificationMessage());
        }
    }
}

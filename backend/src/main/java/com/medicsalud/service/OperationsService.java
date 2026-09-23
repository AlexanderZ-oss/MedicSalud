package com.medicsalud.service;

import com.medicsalud.model.Appointment;
import com.medicsalud.model.Doctor;
import com.medicsalud.model.MedicalService;
import com.medicsalud.model.Product;
import com.medicsalud.repository.AppointmentRepository;
import com.medicsalud.repository.DoctorRepository;
import com.medicsalud.repository.MedicalServiceRepository;
import com.medicsalud.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class OperationsService {
    private final ProductRepository productRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalServiceRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;

    public OperationsService(ProductRepository productRepository,
                             DoctorRepository doctorRepository,
                             MedicalServiceRepository serviceRepository,
                             AppointmentRepository appointmentRepository) {
        this.productRepository = productRepository;
        this.doctorRepository = doctorRepository;
        this.serviceRepository = serviceRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public Appointment scheduleAppointment(String patientName, String patientDni, String symptoms,
                                           int patientAge, Long serviceId, LocalDate date) {
        if (patientName == null || patientName.isBlank() || patientDni == null || patientDni.isBlank()
                || symptoms == null || symptoms.isBlank() || patientAge < 0 || patientAge > 120) {
            throw new IllegalArgumentException("Datos del paciente incompletos");
        }
        MedicalService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
        if (!service.isActive()) {
            throw new IllegalArgumentException("Servicio no disponible");
        }
        LocalDate appointmentDate = date == null ? LocalDate.now().plusDays(1) : date;
        LocalDateTime from = appointmentDate.atStartOfDay();
        LocalDateTime to = appointmentDate.atTime(LocalTime.MAX);
        for (Doctor doctor : doctorRepository.findByAvailableTrue()) {
            if (appointmentRepository.findByDoctorIdAndScheduledAtBetween(doctor.getId(), from, to).isEmpty()) {
                Appointment appointment = new Appointment();
                appointment.setPatientName(patientName.trim());
                appointment.setPatientDni(patientDni.trim());
                appointment.setSymptoms(symptoms.trim());
                appointment.setPatientAge(patientAge);
                appointment.setService(service);
                appointment.setDoctor(doctor);
                appointment.setScheduledAt(appointmentDate.atTime(LocalTime.of(9, 0)));
                appointment.setStatus(Appointment.Status.SCHEDULED);
                return appointmentRepository.save(appointment);
            }
        }
        throw new IllegalStateException("No hay médicos disponibles para esa fecha");
    }

    public List<Appointment> appointmentsForDni(String dni) {
        return appointmentRepository.findByPatientDniOrderByScheduledAtDesc(dni.trim());
    }

    public long lowStockCount() {
        return productRepository.findAll().stream().filter(product -> product.getStock() < 20).count();
    }
}

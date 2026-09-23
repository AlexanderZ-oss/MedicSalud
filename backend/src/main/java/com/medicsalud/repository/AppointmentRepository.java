package com.medicsalud.repository;

import com.medicsalud.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPatientDniOrderByScheduledAtDesc(String patientDni);
    List<Appointment> findByDoctorIdAndScheduledAtBetween(Long doctorId, LocalDateTime from, LocalDateTime to);
}

package com.medicsalud.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments", indexes = @Index(name = "idx_appointment_start", columnList = "scheduledAt"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String patientName;
    @Column(nullable = false, length = 20)
    private String patientDni;
    @Column(nullable = false, length = 500)
    private String symptoms;
    @Column(nullable = false)
    private int patientAge;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MedicalService service;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Doctor doctor;
    @Column(nullable = false)
    private LocalDateTime scheduledAt;
    @Column(length = 500)
    private String notificationMessage;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;
    public enum Status { SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, RESCHEDULED }
}

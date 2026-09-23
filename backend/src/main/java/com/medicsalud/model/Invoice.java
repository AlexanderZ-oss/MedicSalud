package com.medicsalud.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices", indexes = @Index(name = "idx_invoice_dni", columnList = "patientDni"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String patientName;
    @Column(nullable = false, length = 20)
    private String patientDni;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false, length = 30)
    private String deliveryMode;
    @Column(nullable = false)
    private boolean prescriptionRequired;
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
package com.medicsalud.repository;

import com.medicsalud.model.MedicalService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalServiceRepository extends JpaRepository<MedicalService, Long> {
}

package com.smarthospital.clinical.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "diagnosis_catalog")
public class DiagnosisCatalog {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code_system", nullable = false, length = 32)
    private String codeSystem;

    @Column(name = "diagnosis_code", nullable = false, length = 32)
    private String diagnosisCode;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected DiagnosisCatalog() {
    }

    public UUID getId() { return id; }
    public String getCodeSystem() { return codeSystem; }
    public String getDiagnosisCode() { return diagnosisCode; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}

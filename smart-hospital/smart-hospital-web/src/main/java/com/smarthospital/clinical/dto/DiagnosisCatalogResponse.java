package com.smarthospital.clinical.dto;

import java.util.UUID;

public class DiagnosisCatalogResponse {
    public UUID id;
    public String codeSystem;
    public String diagnosisCode;
    public String description;
    public boolean active;
}

package com.smarthospital.admission.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public class BedAssignRequest { @NotNull public UUID bedId; @Size(max=500) public String reason; }

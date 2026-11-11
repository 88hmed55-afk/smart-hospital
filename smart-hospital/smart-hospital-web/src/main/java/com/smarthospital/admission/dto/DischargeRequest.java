package com.smarthospital.admission.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public class DischargeRequest { @NotBlank public String disposition; @Size(max=10000) public String summary; }

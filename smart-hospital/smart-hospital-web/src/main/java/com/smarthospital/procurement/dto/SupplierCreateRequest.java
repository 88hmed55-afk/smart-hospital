package com.smarthospital.procurement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SupplierCreateRequest {
    @NotBlank @Size(max = 32) public String supplierCode;
    @NotBlank @Size(max = 180) public String supplierName;
    @Size(max = 140) public String contactPerson;
    @Size(max = 32) public String phoneNumber;
    @Email @Size(max = 254) public String email;
    @Size(max = 500) public String address;
    @Size(max = 80) public String registrationNumber;
}

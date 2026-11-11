package com.smarthospital.procurement.dto;

import com.smarthospital.procurement.entity.Supplier;
import java.util.UUID;

public class SupplierResponse {
    public UUID id; public String supplierCode; public String supplierName; public String contactPerson;
    public String phoneNumber; public String email; public String address; public String registrationNumber; public String status;
    public static SupplierResponse from(Supplier supplier) {
        SupplierResponse x = new SupplierResponse(); x.id=supplier.getId(); x.supplierCode=supplier.getSupplierCode();
        x.supplierName=supplier.getSupplierName(); x.contactPerson=supplier.getContactPerson(); x.phoneNumber=supplier.getPhoneNumber();
        x.email=supplier.getEmail(); x.address=supplier.getAddress(); x.registrationNumber=supplier.getRegistrationNumber(); x.status=supplier.getStatus(); return x;
    }
}

package com.smarthospital.procurement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "suppliers")
public class Supplier {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "supplier_code", nullable = false, unique = true, length = 32)
    private String supplierCode;
    @Column(name = "supplier_name", nullable = false, length = 180)
    private String supplierName;
    @Column(name = "contact_person", length = 140)
    private String contactPerson;
    @Column(name = "phone_number", length = 32)
    private String phoneNumber;
    @Column(name = "email", length = 254)
    private String email;
    @Column(name = "address_text", length = 500)
    private String address;
    @Column(name = "tax_or_registration_no", length = 80)
    private String registrationNumber;
    @Column(name = "supplier_status", nullable = false, length = 16)
    private String status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Supplier() { }

    public static Supplier create(String code, String name, String contact, String phone,
                                  String email, String address, String registrationNumber) {
        Supplier supplier = new Supplier();
        supplier.id = UUID.randomUUID();
        supplier.supplierCode = code;
        supplier.supplierName = name;
        supplier.contactPerson = contact;
        supplier.phoneNumber = phone;
        supplier.email = email;
        supplier.address = address;
        supplier.registrationNumber = registrationNumber;
        supplier.status = "ACTIVE";
        return supplier;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getSupplierCode() { return supplierCode; }
    public String getSupplierName() { return supplierName; }
    public String getContactPerson() { return contactPerson; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getStatus() { return status; }
}

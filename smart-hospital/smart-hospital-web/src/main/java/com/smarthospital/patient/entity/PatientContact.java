package com.smarthospital.patient.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "patient_contacts")
public class PatientContact {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 24)
    private ContactType contactType;

    @Column(name = "contact_name", length = 160)
    private String contactName;

    @Column(name = "relationship", length = 60)
    private String relationship;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PatientContact() { }

    public PatientContact(ContactType contactType, String contactName, String relationship,
                          String phoneNumber, String email, boolean primary) {
        this.contactType = contactType;
        this.contactName = contactName;
        this.relationship = relationship;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.primary = primary;
    }

    void attachTo(Patient patient) { this.patient = patient; }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ContactType getContactType() { return contactType; }
    public String getContactName() { return contactName; }
    public String getRelationship() { return relationship; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public boolean isPrimary() { return primary; }
    public Instant getCreatedAt() { return createdAt; }
}

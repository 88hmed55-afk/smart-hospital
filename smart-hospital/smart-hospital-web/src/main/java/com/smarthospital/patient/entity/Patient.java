package com.smarthospital.patient.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "patients")
public class Patient {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "mrn", nullable = false, unique = true, length = 32)
    private String mrn;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "middle_name", length = 80)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 16)
    private PatientSex sex;

    @Column(name = "blood_group", length = 3)
    private String bloodGroup;

    @Column(name = "primary_phone", length = 32)
    private String primaryPhone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "address_text", length = 500)
    private String addressText;

    @Column(name = "preferred_language", nullable = false, length = 8)
    private String preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_status", nullable = false, length = 20)
    private PatientStatus patientStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "patient", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("createdAt ASC")
    private List<PatientContact> contacts = new ArrayList<>();

    @OneToMany(mappedBy = "patient", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("recordedAt DESC")
    private List<PatientAllergy> allergies = new ArrayList<>();

    protected Patient() { }

    public static Patient register(String mrn, String firstName, String middleName, String lastName,
                                   LocalDate dateOfBirth, PatientSex sex, String bloodGroup,
                                   String primaryPhone, String email, String addressText,
                                   String preferredLanguage) {
        Patient patient = new Patient();
        patient.mrn = mrn;
        patient.firstName = firstName;
        patient.middleName = middleName;
        patient.lastName = lastName;
        patient.dateOfBirth = dateOfBirth;
        patient.sex = sex;
        patient.bloodGroup = bloodGroup;
        patient.primaryPhone = primaryPhone;
        patient.email = email;
        patient.addressText = addressText;
        patient.preferredLanguage = preferredLanguage == null ? "ar" : preferredLanguage;
        patient.patientStatus = PatientStatus.ACTIVE;
        return patient;
    }

    public void addContact(PatientContact contact) {
        contacts.add(contact);
        contact.attachTo(this);
    }

    public void addAllergy(PatientAllergy allergy) {
        allergies.add(allergy);
        allergy.attachTo(this);
    }

    public void updateDemographics(String firstName, String middleName, String lastName,
                                   LocalDate dateOfBirth, PatientSex sex, String bloodGroup,
                                   String primaryPhone, String email, String addressText,
                                   String preferredLanguage) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.sex = sex;
        this.bloodGroup = bloodGroup;
        this.primaryPhone = primaryPhone;
        this.email = email;
        this.addressText = addressText;
        this.preferredLanguage = preferredLanguage == null ? "ar" : preferredLanguage;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getMrn() { return mrn; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public PatientSex getSex() { return sex; }
    public String getBloodGroup() { return bloodGroup; }
    public String getPrimaryPhone() { return primaryPhone; }
    public String getEmail() { return email; }
    public String getAddressText() { return addressText; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public PatientStatus getPatientStatus() { return patientStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PatientContact> getContacts() { return contacts; }
    public List<PatientAllergy> getAllergies() { return allergies; }
}

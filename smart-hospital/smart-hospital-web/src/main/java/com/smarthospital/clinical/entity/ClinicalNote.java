package com.smarthospital.clinical.entity;

import com.smarthospital.organization.entity.StaffMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Draft notes can be signed; signed notes are corrected through separate amendments. */
@Entity
@Table(schema = "hospital", name = "clinical_notes")
public class ClinicalNote {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false, updatable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_staff_id", nullable = false, updatable = false)
    private StaffMember author;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 24, updatable = false)
    private ClinicalNoteType noteType;

    @Column(name = "note_text", nullable = false, columnDefinition = "text")
    private String noteText;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_status", nullable = false, length = 16)
    private ClinicalNoteStatus noteStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amended_from_id", updatable = false)
    private ClinicalNote amendedFrom;

    protected ClinicalNote() {
    }

    public static ClinicalNote draft(
            Encounter encounter,
            StaffMember author,
            ClinicalNoteType type,
            String text,
            ClinicalNote amendedFrom) {
        ClinicalNote note = new ClinicalNote();
        note.encounter = encounter;
        note.author = author;
        note.noteType = type;
        note.noteText = text;
        note.noteStatus = ClinicalNoteStatus.DRAFT;
        note.amendedFrom = amendedFrom;
        return note;
    }

    public void sign() {
        if (noteStatus != ClinicalNoteStatus.DRAFT) {
            throw new IllegalStateException("Only a draft clinical note can be signed.");
        }
        noteStatus = amendedFrom == null ? ClinicalNoteStatus.SIGNED : ClinicalNoteStatus.AMENDED;
        signedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getEncounterId() { return encounter.getId(); }
    public UUID getAuthorStaffId() { return author.getId(); }
    public ClinicalNoteType getNoteType() { return noteType; }
    public String getNoteText() { return noteText; }
    public ClinicalNoteStatus getNoteStatus() { return noteStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSignedAt() { return signedAt; }
    public UUID getAmendedFromId() { return amendedFrom == null ? null : amendedFrom.getId(); }
    public ClinicalNote getAmendedFrom() { return amendedFrom; }
}

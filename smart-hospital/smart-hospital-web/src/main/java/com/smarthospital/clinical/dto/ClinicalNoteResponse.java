package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.ClinicalNote;
import java.time.Instant;
import java.util.UUID;

public class ClinicalNoteResponse {
    public UUID id;
    public UUID encounterId;
    public UUID authorStaffId;
    public String noteType;
    public String noteText;
    public String noteStatus;
    public Instant createdAt;
    public Instant signedAt;
    public UUID amendedFromId;

    public static ClinicalNoteResponse from(ClinicalNote n) {
        ClinicalNoteResponse r=new ClinicalNoteResponse();
        r.id=n.getId(); r.encounterId=n.getEncounterId(); r.authorStaffId=n.getAuthorStaffId();
        r.noteType=n.getNoteType().name(); r.noteText=n.getNoteText(); r.noteStatus=n.getNoteStatus().name();
        r.createdAt=n.getCreatedAt(); r.signedAt=n.getSignedAt(); r.amendedFromId=n.getAmendedFromId();
        return r;
    }
}

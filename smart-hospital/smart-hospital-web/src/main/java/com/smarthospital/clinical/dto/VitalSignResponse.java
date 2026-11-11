package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.VitalSign;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class VitalSignResponse {
    public UUID id;
    public UUID encounterId;
    public UUID measuredBy;
    public Instant measuredAt;
    public BigDecimal temperatureC;
    public Integer systolicMmhg;
    public Integer diastolicMmhg;
    public Integer pulseBpm;
    public Integer respiratoryRate;
    public BigDecimal oxygenSaturation;
    public BigDecimal weightKg;
    public BigDecimal heightCm;
    public String notes;

    public static VitalSignResponse from(VitalSign v) {
        VitalSignResponse r = new VitalSignResponse();
        r.id=v.getId(); r.encounterId=v.getEncounterId(); r.measuredBy=v.getMeasuredBy(); r.measuredAt=v.getMeasuredAt();
        r.temperatureC=v.getTemperatureC(); r.systolicMmhg=v.getSystolicMmhg(); r.diastolicMmhg=v.getDiastolicMmhg();
        r.pulseBpm=v.getPulseBpm(); r.respiratoryRate=v.getRespiratoryRate(); r.oxygenSaturation=v.getOxygenSaturation();
        r.weightKg=v.getWeightKg(); r.heightCm=v.getHeightCm(); r.notes=v.getNotes();
        return r;
    }
}

package com.smarthospital.pharmacy.dto;

import com.smarthospital.pharmacy.entity.Medication;
import java.util.UUID;

public class MedicationResponse {
    public UUID id;
    public String code;
    public String genericName;
    public String brandName;
    public String strength;
    public String dosageForm;
    public String route;
    public String dispensingUnit;
    public boolean controlled;

    public static MedicationResponse from(Medication medication) {
        MedicationResponse response = new MedicationResponse();
        response.id = medication.getId();
        response.code = medication.getCode();
        response.genericName = medication.getGenericName();
        response.brandName = medication.getBrandName();
        response.strength = medication.getStrength();
        response.dosageForm = medication.getDosageForm();
        response.route = medication.getRoute();
        response.dispensingUnit = medication.getDispensingUnit();
        response.controlled = medication.isControlled();
        return response;
    }
}

package com.smarthospital.pharmacy.dto;

import com.smarthospital.pharmacy.entity.InventoryLocation;
import java.util.UUID;

public class PharmacyLocationResponse {
    public UUID id;
    public String code;
    public String name;

    public static PharmacyLocationResponse from(InventoryLocation location) {
        PharmacyLocationResponse response = new PharmacyLocationResponse();
        response.id = location.getId();
        response.code = location.getCode();
        response.name = location.getName();
        return response;
    }
}

package com.smarthospital.admission.dto;
import java.util.UUID;
public class BedResponse { public UUID id; public UUID branchId; public UUID wardId; public String wardName; public String roomNumber; public String bedNumber; public String bedType; public String operationalStatus; public boolean occupied; public boolean available; }

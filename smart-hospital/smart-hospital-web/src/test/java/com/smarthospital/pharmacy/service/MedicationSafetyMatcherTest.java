package com.smarthospital.pharmacy.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicationSafetyMatcherTest {

    @Test
    void matchesTheGenericNameCaseInsensitively() {
        assertTrue(MedicationSafetyService.matchesAllergen(
                "Amoxicillin", "Amoxil", "Penicillin", "amoxicillin"));
    }

    @Test
    void matchesTheBrandName() {
        assertTrue(MedicationSafetyService.matchesAllergen(
                "Paracetamol", "Panadol", "Analgesic", "PANADOL"));
    }

    @Test
    void matchesTheDrugClass() {
        assertTrue(MedicationSafetyService.matchesAllergen(
                "Ibuprofen", null, "NSAID", "nsaid"));
    }

    @Test
    void matchesWhenTheAllergenIsMoreSpecificThanTheRecordedName() {
        assertTrue(MedicationSafetyService.matchesAllergen(
                "Penicillin G", null, "Penicillin", "penicillin allergy"));
    }

    @Test
    void ignoresAllergensShorterThanThreeCharacters() {
        assertFalse(MedicationSafetyService.matchesAllergen(
                "Amoxicillin", null, "Penicillin", "am"));
    }

    @Test
    void doesNotMatchUnrelatedMedications() {
        assertFalse(MedicationSafetyService.matchesAllergen(
                "Metformin", "Glucophage", "Biguanide", "penicillin"));
    }

    @Test
    void handlesNullFieldsWithoutThrowing() {
        assertFalse(MedicationSafetyService.matchesAllergen(
                null, null, null, "penicillin"));
        assertFalse(MedicationSafetyService.matchesAllergen(
                "Amoxicillin", null, null, null));
    }
}

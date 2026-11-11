package com.smarthospital.pharmacy.service;

import com.smarthospital.common.persistence.DbValues;
import com.smarthospital.pharmacy.entity.Medication;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Clinical medication-safety screening performed before a prescription is written.
 *
 * <p>Two independent checks run:</p>
 * <ol>
 *   <li>Documented patient allergy against generic name, brand name or drug class.</li>
 *   <li>Drug-drug interaction between the new items and the patient's active therapy.</li>
 * </ol>
 *
 * <p>Findings are classified as blocking (contraindicated interaction, major interaction
 * or a documented/verified allergy) or advisory. A prescriber may override a block only
 * by supplying a documented clinical justification, which is written to the tamper-evident
 * audit trail. The interaction catalogue is data-driven; production deployments must load
 * a licensed drug-interaction feed rather than rely on the synthetic demo rows.</p>
 */
@Stateless
public class MedicationSafetyService {

    private static final Set<String> BLOCKING_INTERACTION_SEVERITIES = Set.of("CONTRAINDICATED", "MAJOR");
    private static final Set<String> BLOCKING_ALLERGY_SEVERITIES = Set.of("SEVERE", "LIFE_THREATENING", "ANAPHYLAXIS");
    private static final Set<String> REFUTED_ALLERGY_STATUSES = Set.of("REFUTED", "ENTERED_IN_ERROR", "RULED_OUT");

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    public record SafetyWarning(String code, String severity, String message) { }

    public record Assessment(List<SafetyWarning> warnings, List<SafetyWarning> blockers) {
        public boolean hasBlockers() {
            return !blockers.isEmpty();
        }
    }

    /**
     * Evaluates the proposed medications for a patient.
     *
     * @param patientId   patient whose allergy record and active therapy are screened
     * @param medications medications about to be prescribed
     */
    public Assessment assess(UUID patientId, Collection<Medication> medications) {
        List<SafetyWarning> warnings = new ArrayList<>();
        List<SafetyWarning> blockers = new ArrayList<>();

        collectAllergyFindings(patientId, medications, warnings, blockers);
        collectInteractionFindings(patientId, medications, warnings, blockers);

        return new Assessment(List.copyOf(warnings), List.copyOf(blockers));
    }

    private void collectAllergyFindings(UUID patientId, Collection<Medication> medications,
                                        List<SafetyWarning> warnings, List<SafetyWarning> blockers) {
        @SuppressWarnings("unchecked")
        List<Object[]> allergies = em.createNativeQuery("""
                SELECT allergen_name, severity, reaction, verification_status
                  FROM hospital.patient_allergies
                 WHERE patient_id = ?1
                """)
                .setParameter(1, patientId)
                .getResultList();

        for (Object[] row : allergies) {
            String allergen = (String) row[0];
            String severity = upper((String) row[1]);
            String reaction = (String) row[2];
            String verification = upper((String) row[3]);
            if (allergen == null || allergen.isBlank() || REFUTED_ALLERGY_STATUSES.contains(verification)) {
                continue;
            }
            for (Medication medication : medications) {
                if (!matchesAllergen(medication, allergen)) {
                    continue;
                }
                String message = "Patient has a documented allergy to " + allergen
                        + " which matches " + medication.getGenericName()
                        + (reaction == null || reaction.isBlank() ? "." : " (reaction: " + reaction + ").");
                SafetyWarning warning = new SafetyWarning("ALLERGY", severity == null ? "UNKNOWN" : severity, message);
                boolean verified = "VERIFIED".equals(verification) || "CONFIRMED".equals(verification);
                if (verified || BLOCKING_ALLERGY_SEVERITIES.contains(severity)) {
                    blockers.add(warning);
                } else {
                    warnings.add(warning);
                }
            }
        }
    }

    private void collectInteractionFindings(UUID patientId, Collection<Medication> medications,
                                            List<SafetyWarning> warnings, List<SafetyWarning> blockers) {
        Map<UUID, String> namesById = new LinkedHashMap<>();
        for (Medication medication : medications) {
            namesById.put(medication.getId(), medication.getGenericName());
        }
        Set<UUID> screenIds = new LinkedHashSet<>(namesById.keySet());
        screenIds.addAll(activeTherapyMedicationIds(patientId));
        if (screenIds.size() < 2) {
            return;
        }

        List<UUID> ids = List.copyOf(screenIds);
        String placeholders = IntStream.rangeClosed(1, ids.size())
                .mapToObj(index -> "?" + index)
                .collect(Collectors.joining(", "));
        String sql = "SELECT medication_id, interacting_medication_id, severity, description"
                + "  FROM hospital.drug_interactions"
                + " WHERE is_active = TRUE"
                + "   AND medication_id IN (" + placeholders + ")"
                + "   AND interacting_medication_id IN (" + placeholders + ")";

        var query = em.createNativeQuery(sql);
        for (int index = 0; index < ids.size(); index++) {
            query.setParameter(index + 1, ids.get(index));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> interactions = query.getResultList();
        for (Object[] row : interactions) {
            UUID first = DbValues.uuid(row[0]);
            UUID second = DbValues.uuid(row[1]);
            // Only surface interactions that involve at least one newly prescribed item.
            if (!namesById.containsKey(first) && !namesById.containsKey(second)) {
                continue;
            }
            String severity = upper((String) row[2]);
            String description = (String) row[3];
            String message = "Potential " + (severity == null ? "" : severity + " ")
                    + "interaction between " + name(namesById, first) + " and " + name(namesById, second)
                    + ": " + (description == null ? "pharmacist review required." : description);
            SafetyWarning warning = new SafetyWarning("DRUG_INTERACTION", severity, message);
            if (BLOCKING_INTERACTION_SEVERITIES.contains(severity)) {
                blockers.add(warning);
            } else {
                warnings.add(warning);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> activeTherapyMedicationIds(UUID patientId) {
        List<?> ids = em.createNativeQuery("""
                SELECT DISTINCT pi.medication_id
                  FROM hospital.prescription_items pi
                  JOIN hospital.prescriptions p ON p.id = pi.prescription_id
                  JOIN hospital.encounters e ON e.id = p.encounter_id
                 WHERE e.patient_id = ?1
                   AND p.status IN ('ACTIVE', 'PARTIALLY_DISPENSED')
                """)
                .setParameter(1, patientId)
                .getResultList();
        return ids.stream().map(DbValues::uuid).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String name(Map<UUID, String> namesById, UUID id) {
        return namesById.getOrDefault(id, "another medication");
    }

    static boolean matchesAllergen(Medication medication, String allergen) {
        return matchesAllergen(medication.getGenericName(), medication.getBrandName(),
                medication.getDrugClass(), allergen);
    }

    static boolean matchesAllergen(String genericName, String brandName, String drugClass, String allergen) {
        String needle = normalize(allergen);
        if (needle.length() < 3) {
            return false;
        }
        return overlap(normalize(genericName), needle)
                || overlap(normalize(brandName), needle)
                || overlap(normalize(drugClass), needle);
    }

    private static boolean overlap(String candidate, String needle) {
        if (candidate == null || candidate.length() < 3) {
            return false;
        }
        return candidate.contains(needle) || needle.contains(candidate);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}

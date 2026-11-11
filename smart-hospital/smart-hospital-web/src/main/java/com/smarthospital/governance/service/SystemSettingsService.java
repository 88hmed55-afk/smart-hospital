package com.smarthospital.governance.service;

import com.smarthospital.common.persistence.DbValues;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.governance.dto.SystemSettingResponse;
import com.smarthospital.governance.dto.SystemSettingUpdateRequest;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Stateless
public class SystemSettingsService {
    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("analytics.bed_occupancy.warning_percent", "حد تنبيه إشغال الأسرة",
                    "تظهر بطاقة تنبيه عندما تتجاوز نسبة إشغال الأسرة هذا الحد.", "%", new BigDecimal("85"), new BigDecimal("50"), new BigDecimal("100")),
            new Definition("analytics.lab_pending.warning_count", "حد تراكم طلبات المختبر",
                    "عدد الطلبات المعلقة الذي يستدعي تنبيهاً تشغيلياً.", "طلب", new BigDecimal("20"), BigDecimal.ONE, new BigDecimal("1000")),
            new Definition("analytics.lab_tat.warning_minutes", "حد زمن إنجاز المختبر",
                    "المدة بالدقائق التي بعدها يعتبر متوسط إنجاز المختبر متأخراً.", "دقيقة", new BigDecimal("240"), new BigDecimal("30"), new BigDecimal("1440"))
    );

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;
    @EJB private CurrentUserService currentUserService;
    @EJB private AuditService audit;
    public List<SystemSettingResponse> list(UUID requestedBranchId) {
        CurrentUser user = currentUserService.requireCurrentUser();
        UUID branchId = requestedBranchId == null ? user.defaultBranchId() : requestedBranchId;
        currentUserService.requireBranchAccess(branchId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT setting_key, JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$.value')), branch_id, updated_at
                  FROM hospital.system_settings
                 WHERE branch_id = ?1 OR branch_id IS NULL
                """)
                .setParameter(1, branchId)
                .getResultList();
        Map<String, BigDecimal> globalValues = new LinkedHashMap<>();
        Map<String, BigDecimal> branchValues = new LinkedHashMap<>();
        Map<String, Instant> branchUpdated = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = (String) row[0];
            BigDecimal value = parseDecimal(row[1]);
            UUID rowBranch = DbValues.uuid(row[2]);
            if (rowBranch == null) globalValues.put(key, value);
            else {
                branchValues.put(key, value);
                branchUpdated.put(key, toInstant(row[3]));
            }
        }

        List<SystemSettingResponse> responses = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            BigDecimal global = globalValues.get(definition.key());
            BigDecimal branch = branchValues.get(definition.key());
            BigDecimal effective = branch != null ? branch : global != null ? global : definition.defaultValue();
            SystemSettingResponse response = new SystemSettingResponse();
            response.key = definition.key();
            response.label = definition.label();
            response.description = definition.description();
            response.unit = definition.unit();
            response.defaultValue = definition.defaultValue();
            response.globalValue = global;
            response.branchValue = branch;
            response.effectiveValue = effective;
            response.effectiveSource = branch != null ? "BRANCH" : global != null ? "GLOBAL" : "DEFAULT";
            response.minimum = definition.minimum();
            response.maximum = definition.maximum();
            response.updatedAt = branchUpdated.get(definition.key());
            responses.add(response);
        }
        return responses;
    }
    public List<SystemSettingResponse> update(SystemSettingUpdateRequest request) {
        CurrentUser user = currentUserService.requireCurrentUser();
        Definition definition = DEFINITIONS.stream()
                .filter(candidate -> candidate.key().equals(request.key))
                .findFirst()
                .orElseThrow(() -> BusinessException.badRequest("SETTING_NOT_CONFIGURABLE", "This setting is not exposed for editing."));
        if (request.value == null || request.value.compareTo(definition.minimum()) < 0
                || request.value.compareTo(definition.maximum()) > 0) {
            throw BusinessException.badRequest("SETTING_VALUE_OUT_OF_RANGE",
                    "The value must be between " + definition.minimum().toPlainString() + " and " + definition.maximum().toPlainString() + ".");
        }

        UUID branchId = request.branchId;
        if (branchId == null) {
            if (!user.roleCodes().contains("HOSPITAL_ADMIN")) {
                throw BusinessException.forbidden("GLOBAL_SETTING_ADMIN_ONLY", "Only a hospital administrator can change a global setting.");
            }
            em.createNativeQuery("""
                    INSERT INTO hospital.system_settings
                        (setting_key, branch_id, setting_value, description, updated_by, updated_at)
                    VALUES (?1, NULL, JSON_OBJECT('value', CAST(?2 AS DECIMAL(12,2))), ?3, ?4, now())
                    ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value), description=VALUES(description), updated_by=VALUES(updated_by), updated_at=CURRENT_TIMESTAMP(6)
                    """)
                    .setParameter(1, definition.key())
                    .setParameter(2, request.value.toPlainString())
                    .setParameter(3, definition.description())
                    .setParameter(4, user.userId())
                    .executeUpdate();
        } else {
            currentUserService.requireBranchAccess(branchId);
            em.createNativeQuery("""
                    INSERT INTO hospital.system_settings
                        (setting_key, branch_id, setting_value, description, updated_by, updated_at)
                    VALUES (?1, ?2, JSON_OBJECT('value', CAST(?3 AS DECIMAL(12,2))), ?4, ?5, now())
                    ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value), description=VALUES(description), updated_by=VALUES(updated_by), updated_at=CURRENT_TIMESTAMP(6)
                    """)
                    .setParameter(1, definition.key())
                    .setParameter(2, branchId)
                    .setParameter(3, request.value.toPlainString())
                    .setParameter(4, definition.description())
                    .setParameter(5, user.userId())
                    .executeUpdate();
        }
        String auditContext = "key=" + definition.key() + ";scope=" + (branchId == null ? "GLOBAL" : "BRANCH");
        if (branchId == null) audit.recordGlobalAction("SYSTEM_SETTING_UPDATED", "SYSTEM_SETTING", null, auditContext);
        else audit.recordAction("SYSTEM_SETTING_UPDATED", "SYSTEM_SETTING", null, auditContext, branchId);
        return list(branchId);
    }

    private BigDecimal parseDecimal(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException exception) { return null; }
    }

    private Instant toInstant(Object value) {
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return null;
    }

    private record Definition(String key, String label, String description, String unit,
                              BigDecimal defaultValue, BigDecimal minimum, BigDecimal maximum) { }
}

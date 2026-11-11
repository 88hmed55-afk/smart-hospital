package com.smarthospital.analytics.service;

import com.smarthospital.common.persistence.DbValues;

import com.smarthospital.analytics.dto.DashboardResponse;
import com.smarthospital.analytics.dto.DecisionCardResponse;
import com.smarthospital.analytics.dto.DecisionCardStatusRequest;
import com.smarthospital.audit.service.AuditService;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.organization.entity.HospitalBranch;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class OperationalAnalyticsService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private CurrentUserService users;

    @EJB
    private AuditService audit;

    public DashboardResponse getDashboard(UUID branchId) {
        if (branchId == null) {
            throw BusinessException.badRequest("BRANCH_REQUIRED", "A hospital branch is required.");
        }
        users.requireBranchAccess(branchId);
        CurrentUser actor = users.requireCurrentUser();
        if (!hasAnyRole(actor, "HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "ACCOUNTANT")) {
            throw BusinessException.forbidden("ANALYTICS_ROLE_REQUIRED", "Operational management analytics are restricted to authorized management and finance roles.");
        }

        HospitalBranch branch = em.find(HospitalBranch.class, branchId);
        if (branch == null || !branch.isActive()) {
            throw BusinessException.notFound("Active hospital branch");
        }
        ZoneId zone = safeZone(branch.getTimezone());
        LocalDate localDate = LocalDate.now(zone);
        Instant start = localDate.atStartOfDay(zone).toInstant();
        Instant end = localDate.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = Instant.now();

        DashboardResponse result = new DashboardResponse();
        result.branchId = branchId;
        result.branchName = branch.getName();
        result.currencyCode = branch.getDefaultCurrency();
        result.periodStart = start;
        result.periodEnd = end;
        result.appointmentsToday = count("""
                SELECT count(*) FROM hospital.appointments
                 WHERE branch_id=?1 AND starts_at>=?2 AND starts_at<?3
                """, branchId, start, end);
        result.checkedInAppointments = count("""
                SELECT count(*) FROM hospital.appointments
                 WHERE branch_id=?1 AND starts_at>=?2 AND starts_at<?3
                   AND status='CHECKED_IN'
                """, branchId, start, end);
        result.visitsToday = count("""
                SELECT count(*) FROM hospital.encounters
                 WHERE branch_id=?1 AND started_at>=?2 AND started_at<?3
                """, branchId, start, end);
        result.activeAdmissions = count("""
                SELECT count(*) FROM hospital.admissions
                 WHERE branch_id=?1 AND admission_status='ADMITTED'
                """, branchId, null, null);
        result.operationalBeds = count("""
                SELECT count(*) FROM hospital.beds
                 WHERE branch_id=?1 AND is_active=TRUE
                   AND operational_status <> 'OUT_OF_SERVICE'
                """, branchId, null, null);
        result.occupiedBeds = count("""
                SELECT count(*) FROM hospital.bed_assignments ba
                 JOIN hospital.admissions a ON a.id=ba.admission_id AND a.branch_id=ba.branch_id
                 JOIN hospital.beds b ON b.id=ba.bed_id AND b.branch_id=ba.branch_id
                 WHERE ba.branch_id=?1 AND ba.assignment_status='ACTIVE'
                   AND ba.started_at<=?2 AND a.admission_status='ADMITTED'
                   AND b.is_active=TRUE AND b.operational_status <> 'OUT_OF_SERVICE'
                """, branchId, now, null);
        result.bedOccupancyPercent = result.operationalBeds == 0
                ? ZERO
                : BigDecimal.valueOf(result.occupiedBeds)
                    .multiply(new BigDecimal("100"))
                    .divide(BigDecimal.valueOf(result.operationalBeds), 2, RoundingMode.HALF_UP);
        result.pendingLabItems = count("""
                SELECT count(*) FROM hospital.lab_order_items loi
                 JOIN hospital.lab_orders lo ON lo.id=loi.lab_order_id
                 JOIN hospital.encounters e ON e.id=lo.encounter_id
                 WHERE e.branch_id=?1 AND loi.status IN ('PENDING','COLLECTED','PROCESSING','RESULTED')
                """, branchId, null, null);
        result.labTurnaroundSamples = count("""
                SELECT count(*) FROM hospital.lab_results lr
                  JOIN hospital.lab_order_items loi ON loi.id=lr.lab_order_item_id
                  JOIN hospital.lab_orders lo ON lo.id=loi.lab_order_id
                  JOIN hospital.encounters e ON e.id=lo.encounter_id
                 WHERE e.branch_id=?1 AND lr.released_at>=?2 AND lr.released_at<?3
                   AND loi.collected_at IS NOT NULL AND lr.released_at>=loi.collected_at
                """, branchId, start, end);
        result.averageLabTurnaroundMinutes = decimal("""
                SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, loi.collected_at, lr.released_at)/60.0),0)
                  FROM hospital.lab_results lr
                  JOIN hospital.lab_order_items loi ON loi.id=lr.lab_order_item_id
                  JOIN hospital.lab_orders lo ON lo.id=loi.lab_order_id
                  JOIN hospital.encounters e ON e.id=lo.encounter_id
                 WHERE e.branch_id=?1 AND lr.released_at>=?2 AND lr.released_at<?3
                   AND loi.collected_at IS NOT NULL AND lr.released_at>=loi.collected_at
                """, branchId, start, end);
        result.lowStockMedicationPolicies = count("""
                SELECT count(*) FROM (
                    SELECT rp.id, rp.reorder_point,
                           coalesce(sum(greatest(sb.quantity_on_hand-sb.quantity_reserved,0)),0) AS available_quantity
                      FROM hospital.inventory_reorder_policies rp
                      LEFT JOIN hospital.medication_batches mb
                        ON mb.medication_id=rp.medication_id AND mb.expires_on>=CURRENT_DATE
                      JOIN hospital.inventory_locations il
                        ON il.id=rp.location_id AND il.branch_id=rp.branch_id AND il.is_active=TRUE
                      LEFT JOIN hospital.stock_balances sb
                        ON sb.location_id=rp.location_id AND sb.batch_id=mb.id
                     WHERE rp.branch_id=?1 AND rp.is_active=TRUE
                     GROUP BY rp.id, rp.reorder_point
                    HAVING coalesce(sum(greatest(sb.quantity_on_hand-sb.quantity_reserved,0)),0)<=rp.reorder_point
                ) low_stock
                """, branchId, null, null);
        result.postedRevenueToday = decimal("""
                SELECT coalesce(sum(p.amount),0)
                  FROM hospital.payments p
                  JOIN hospital.invoices i ON i.id=p.invoice_id
                 WHERE i.branch_id=?1 AND p.status='POSTED'
                   AND p.paid_at>=?2 AND p.paid_at<?3
                """, branchId, start, end);

        result.openDecisionCards = count("""
                SELECT count(*) FROM hospital.decision_cards
                 WHERE branch_id=?1 AND card_status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS')
                """, branchId, null, null);
        return result;
    }

    public DashboardResponse refreshOperationalSnapshot(UUID branchId) {
        requireManagementRole();
        DashboardResponse result = getDashboard(branchId);
        persistDailySnapshots(branchId, result.periodStart, result.periodEnd, result);
        generateRuleBasedDecisionCards(branchId, result.periodStart, result.periodEnd, result);
        audit.recordAction("OPERATIONAL_ANALYTICS_REFRESHED", "HOSPITAL_BRANCH", branchId,
                "daily operational analytics snapshot refreshed", branchId);
        result.openDecisionCards = count("""
                SELECT count(*) FROM hospital.decision_cards
                 WHERE branch_id=?1 AND card_status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS')
                """, branchId, null, null);
        return result;
    }

    public List<DecisionCardResponse> listDecisionCards(UUID branchId, boolean openOnly, int limit) {
        if (branchId == null) {
            throw BusinessException.badRequest("BRANCH_REQUIRED", "A hospital branch is required.");
        }
        users.requireBranchAccess(branchId);
        requireManagementRole();
        String sql = """
                SELECT id, card_code, category, severity, card_status, title, problem_statement,
                       analysis_summary, recommendation, evidence_window_start, evidence_window_end,
                       observed_value, threshold_value, unit, confidence_percent,
                       CAST(assumptions AS CHAR), CAST(expected_impact AS CHAR), generated_at
                  FROM hospital.decision_cards
                 WHERE branch_id=?1
                """ + (openOnly ? " AND card_status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS') " : " ")
                + " ORDER BY CASE severity WHEN 'CRITICAL' THEN 1 WHEN 'WARNING' THEN 2 ELSE 3 END, generated_at DESC";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, branchId)
                .setMaxResults(Math.min(Math.max(limit, 1), 100))
                .getResultList();
        List<DecisionCardResponse> cards = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            cards.add(toDecisionCard(row));
        }
        return cards;
    }

    public DecisionCardResponse changeDecisionCardStatus(UUID cardId, DecisionCardStatusRequest request) {
        if (cardId == null || request == null || request.status == null) {
            throw BusinessException.badRequest("DECISION_CARD_STATUS_REQUIRED", "A decision card and target status are required.");
        }
        String target = request.status.trim().toUpperCase();
        if (!List.of("ACKNOWLEDGED", "IN_PROGRESS", "COMPLETED", "DISMISSED").contains(target)) {
            throw BusinessException.badRequest("DECISION_CARD_STATUS_INVALID", "Unsupported decision card status.");
        }
        requireManagementRole();
        CurrentUser actor = users.requireCurrentUser();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, branch_id, card_status FROM hospital.decision_cards
                 WHERE id=?1 FOR UPDATE
                """)
                .setParameter(1, cardId)
                .getResultList();
        if (rows.isEmpty()) {
            throw BusinessException.notFound("Decision card");
        }
        Object[] current = rows.get(0);
        UUID branchId = DbValues.uuid(current[1]);
        users.requireBranchAccess(branchId);
        String old = (String) current[2];
        if (!allowedTransition(old, target)) {
            throw BusinessException.conflict("DECISION_CARD_TRANSITION_INVALID", "The requested decision card status transition is not allowed.");
        }
        Instant now = Instant.now();
        em.createNativeQuery("""
                UPDATE hospital.decision_cards
                   SET card_status=?1,
                       acknowledged_by=CASE WHEN ?1='ACKNOWLEDGED' AND acknowledged_by IS NULL THEN ?2 ELSE acknowledged_by END,
                       acknowledged_at=CASE WHEN ?1='ACKNOWLEDGED' AND acknowledged_at IS NULL THEN ?3 ELSE acknowledged_at END,
                       resolved_at=CASE WHEN ?1 IN ('COMPLETED','DISMISSED') THEN ?3 ELSE resolved_at END
                 WHERE id=?4
                """)
                .setParameter(1, target)
                .setParameter(2, actor.userId())
                .setParameter(3, Timestamp.from(now))
                .setParameter(4, cardId)
                .executeUpdate();
        audit.recordAction("DECISION_CARD_STATUS_CHANGED", "DECISION_CARD", cardId,
                "decision card status changed to " + target, branchId);
        return findDecisionCard(cardId, branchId);
    }

    private void persistDailySnapshots(UUID branchId, Instant start, Instant end, DashboardResponse d) {
        saveSnapshot("APPOINTMENTS_TODAY", branchId, start, end, BigDecimal.valueOf(d.appointmentsToday), d.appointmentsToday);
        saveSnapshot("ACTIVE_ADMISSIONS", branchId, start, end, BigDecimal.valueOf(d.activeAdmissions), d.activeAdmissions);
        saveSnapshot("OCCUPIED_BEDS", branchId, start, end, BigDecimal.valueOf(d.occupiedBeds), d.occupiedBeds);
        saveSnapshot("VISITS_TODAY", branchId, start, end, BigDecimal.valueOf(d.visitsToday), d.visitsToday);
        saveSnapshot("BED_OCCUPANCY_PERCENT", branchId, start, end, d.bedOccupancyPercent, d.operationalBeds);
        saveSnapshot("LAB_PENDING_ITEMS", branchId, start, end, BigDecimal.valueOf(d.pendingLabItems), d.pendingLabItems);
        saveSnapshot("LAB_TAT_AVG_MINUTES", branchId, start, end, d.averageLabTurnaroundMinutes, d.labTurnaroundSamples);
        saveSnapshot("LOW_STOCK_POLICIES", branchId, start, end, BigDecimal.valueOf(d.lowStockMedicationPolicies), d.lowStockMedicationPolicies);
        saveSnapshot("POSTED_REVENUE_TODAY", branchId, start, end, d.postedRevenueToday, 0);
    }

    private void saveSnapshot(String code, UUID branchId, Instant start, Instant end, BigDecimal value, long sampleCount) {
        em.createNativeQuery("""
                INSERT INTO hospital.metric_snapshots
                    (metric_definition_id, branch_id, period_granularity, window_start, window_end, metric_value, sample_count)
                SELECT md.id, ?1, 'DAY', ?2, ?3, ?4, ?5
                  FROM hospital.metric_definitions md
                 WHERE md.metric_code=?6 AND md.is_active=TRUE
                ON DUPLICATE KEY UPDATE metric_value=VALUES(metric_value), sample_count=VALUES(sample_count), computed_at=CURRENT_TIMESTAMP(6)
                """)
                .setParameter(1, branchId)
                .setParameter(2, Timestamp.from(start))
                .setParameter(3, Timestamp.from(end))
                .setParameter(4, value)
                .setParameter(5, Math.max(sampleCount, 0))
                .setParameter(6, code)
                .executeUpdate();
        em.createNativeQuery("""
                UPDATE hospital.metric_snapshots ms
                   SET metric_value=?1, sample_count=?2, computed_at=CURRENT_TIMESTAMP(6)
                  FROM hospital.metric_definitions md
                 WHERE ms.metric_definition_id=md.id AND md.metric_code=?3
                   AND ms.branch_id=?4 AND ms.period_granularity='DAY'
                   AND ms.window_start=?5 AND ms.window_end=?6
                   AND ms.department_id IS NULL
                """)
                .setParameter(1, value)
                .setParameter(2, Math.max(sampleCount, 0))
                .setParameter(3, code)
                .setParameter(4, branchId)
                .setParameter(5, Timestamp.from(start))
                .setParameter(6, Timestamp.from(end))
                .executeUpdate();
    }

    private void generateRuleBasedDecisionCards(UUID branchId, Instant start, Instant end, DashboardResponse d) {
        BigDecimal bedThreshold = settingNumber("analytics.bed_occupancy.warning_percent", branchId, new BigDecimal("85"));
        BigDecimal labQueueThreshold = settingNumber("analytics.lab_pending.warning_count", branchId, new BigDecimal("20"));
        BigDecimal labTatThreshold = settingNumber("analytics.lab_tat.warning_minutes", branchId, new BigDecimal("240"));
        if (d.operationalBeds > 0 && d.bedOccupancyPercent.compareTo(bedThreshold) >= 0) {
            createCard(branchId, "BED_OCCUPANCY_HIGH", "BED_OCCUPANCY", "WARNING",
                    "ارتفاع إشغال الأسرة", "نسبة إشغال الأسرة التشغيلية تجاوزت الحد التشغيلي المحدد.",
                    "تم احتساب الأسرة المشغولة مقارنة بالأسرة النشطة التي ليست خارج الخدمة. النسبة المرصودة "
                            + d.bedOccupancyPercent + "% والحد " + bedThreshold + "%.",
                    "راجع خطة الخروج والتحويلات وتوزيع الأسرة، وادرس زيادة القدرة التشغيلية بعد مراجعة الإدارة السريرية.",
                    start, end, d.bedOccupancyPercent, bedThreshold, "%",
                    "[\"المؤشر تشغيلي ولا يقيّم شدة الحالات الطبية\",\"الأسرة خارج الخدمة مستبعدة من المقام\"]",
                    "{\"هدف_المراجعة\":\"تحسين توفر الأسرة وتقليل الاختناق\"}",
                    "BED_OCCUPANCY_PERCENT", "إشغال الأسرة", d.bedOccupancyPercent, bedThreshold);
        }
        if (BigDecimal.valueOf(d.pendingLabItems).compareTo(labQueueThreshold) >= 0) {
            createCard(branchId, "LAB_BACKLOG_HIGH", "LAB_TAT", "WARNING",
                    "تراكم طلبات المختبر", "عدد عناصر طلبات المختبر غير المعتمدة بلغ الحد التشغيلي أو تجاوزه.",
                    "يوجد " + d.pendingLabItems + " عنصراً في حالات الانتظار أو الجمع أو المعالجة أو إدخال النتيجة، مقارنة بحد " + labQueueThreshold + ".",
                    "راجع توزيع مهام المختبر وسعة الورديات والعينات المتأخرة، ثم وثّق الإجراء الإداري المتخذ.",
                    start, end, BigDecimal.valueOf(d.pendingLabItems), labQueueThreshold, "عنصر",
                    "[\"يستبعد العناصر الملغاة والمعتمدة\",\"العدد لا يحدد أولوية الحالة السريرية\"]",
                    "{\"هدف_المراجعة\":\"تقليل تراكم العمل وتحسين زمن إنجاز الفحوصات\"}",
                    "LAB_PENDING_ITEMS", "طلبات المختبر المعلقة", BigDecimal.valueOf(d.pendingLabItems), labQueueThreshold);
        }
        if (d.averageLabTurnaroundMinutes.compareTo(labTatThreshold) >= 0 && d.averageLabTurnaroundMinutes.signum() > 0) {
            createCard(branchId, "LAB_TAT_HIGH", "LAB_TAT", "WARNING",
                    "ارتفاع زمن إنجاز المختبر", "متوسط الزمن بين جمع العينة واعتماد النتيجة تجاوز الحد المحدد.",
                    "متوسط زمن الإنجاز اليوم " + d.averageLabTurnaroundMinutes + " دقيقة؛ والحد " + labTatThreshold + " دقيقة.",
                    "حلل الفحوصات المتأخرة وأوقات الورديات وتوفر الأجهزة والمواد، دون تغيير النتائج أو اعتمادها آلياً.",
                    start, end, d.averageLabTurnaroundMinutes, labTatThreshold, "دقيقة",
                    "[\"يحسب الزمن للعناصر ذات وقت جمع ووقت إصدار معتمد\",\"المتوسط لا يغني عن مراجعة الحالات الفردية\"]",
                    "{\"هدف_المراجعة\":\"تقليل زمن دورة المختبر\"}",
                    "LAB_TAT_AVG_MINUTES", "متوسط زمن المختبر", d.averageLabTurnaroundMinutes, labTatThreshold);
        }
        if (d.lowStockMedicationPolicies > 0) {
            createCard(branchId, "PHARMACY_REORDER_REQUIRED", "PHARMACY_STOCK", "WARNING",
                    "أصناف صيدلية تحت نقطة إعادة الطلب", "يوجد صنف أو أكثر بلغ رصيده المتاح نقطة إعادة الطلب المعرفة.",
                    "عدد سياسات المخزون التي بلغت نقطة إعادة الطلب أو انخفضت عنها: " + d.lowStockMedicationPolicies + ".",
                    "راجع الرصيد والدفعات الصالحة، ثم أنشئ طلب شراء عند الحاجة وفق إجراءات الاعتماد.",
                    start, end, BigDecimal.valueOf(d.lowStockMedicationPolicies), BigDecimal.ZERO, "سياسة مخزون",
                    "[\"يتم احتساب الكمية المتاحة بعد خصم الكميات المحجوزة\",\"تستبعد الدفعات منتهية الصلاحية\"]",
                    "{\"هدف_المراجعة\":\"تقليل احتمالية نفاد المخزون\"}",
                    "LOW_STOCK_POLICIES", "سياسات إعادة الطلب", BigDecimal.valueOf(d.lowStockMedicationPolicies), BigDecimal.ZERO);
        }
    }

    private void createCard(UUID branchId, String code, String category, String severity,
                            String title, String problem, String analysis, String recommendation,
                            Instant start, Instant end, BigDecimal observed, BigDecimal threshold, String unit,
                            String assumptions, String expectedImpact, String metricCode,
                            String evidenceDescription, BigDecimal evidenceValue, BigDecimal evidenceExpected) {
        String dayKey = start.toString();
        String dedupKey = code + ":" + dayKey;
        em.createNativeQuery("""
                INSERT IGNORE INTO hospital.decision_cards
                    (branch_id, card_code, deduplication_key, category, severity, card_status,
                     title, problem_statement, analysis_summary, recommendation,
                     evidence_window_start, evidence_window_end, observed_value, threshold_value,
                     unit, assumptions, expected_impact)
                VALUES
                    (?1, ?2, ?3, ?4, ?5, 'OPEN', ?6, ?7,
                     ?8, ?9, ?10, ?11, ?12, ?13,
                     ?14, CAST(?15 AS JSON), CAST(?16 AS JSON))
                """)
                .setParameter(1, branchId).setParameter(2, code).setParameter(3, dedupKey)
                .setParameter(4, category).setParameter(5, severity).setParameter(6, title)
                .setParameter(7, problem).setParameter(8, analysis).setParameter(9, recommendation)
                .setParameter(10, Timestamp.from(start)).setParameter(11, Timestamp.from(end)).setParameter(12, observed)
                .setParameter(13, threshold).setParameter(14, unit)
                .setParameter(15, assumptions).setParameter(16, expectedImpact).executeUpdate();
        Object cardId = em.createNativeQuery("SELECT id FROM hospital.decision_cards WHERE branch_id=?1 AND deduplication_key=?2")
                .setParameter(1, branchId).setParameter(2, dedupKey).getSingleResult();
        em.createNativeQuery("""
                INSERT IGNORE INTO hospital.decision_card_evidence
                    (decision_card_id, evidence_code, evidence_description, observed_value, expected_value, source_module)
                VALUES (?1, ?2, ?3, ?4, ?5, 'OPERATIONAL_ANALYTICS')
                """)
                .setParameter(1, cardId).setParameter(2, metricCode)
                .setParameter(3, evidenceDescription).setParameter(4, evidenceValue)
                .setParameter(5, evidenceExpected).executeUpdate();
    }

    private BigDecimal settingNumber(String key, UUID branchId, BigDecimal fallback) {
        Object value = em.createNativeQuery("""
                SELECT coalesce(
                    (SELECT JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$.value')) FROM hospital.system_settings
                      WHERE setting_key=?1 AND branch_id=?2 LIMIT 1),
                    (SELECT JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$.value')) FROM hospital.system_settings
                      WHERE setting_key=?1 AND branch_id IS NULL LIMIT 1)
                )
                """)
                .setParameter(1, key)
                .setParameter(2, branchId)
                .getSingleResult();
        if (value == null) return fallback;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long count(String sql, UUID branchId, Instant firstTime, Instant secondTime) {
        var query = em.createNativeQuery(sql).setParameter(1, branchId);
        if (firstTime != null) query.setParameter(2, Timestamp.from(firstTime));
        if (secondTime != null) query.setParameter(3, Timestamp.from(secondTime));
        return ((Number) query.getSingleResult()).longValue();
    }

    private BigDecimal decimal(String sql, UUID branchId, Instant start, Instant end) {
        var query = em.createNativeQuery(sql).setParameter(1, branchId);
        if (start != null) query.setParameter(2, Timestamp.from(start));
        if (end != null) query.setParameter(3, Timestamp.from(end));
        Object value = query.getSingleResult();
        return value == null ? ZERO : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private DecisionCardResponse findDecisionCard(UUID cardId, UUID branchId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, card_code, category, severity, card_status, title, problem_statement,
                       analysis_summary, recommendation, evidence_window_start, evidence_window_end,
                       observed_value, threshold_value, unit, confidence_percent,
                       CAST(assumptions AS CHAR), CAST(expected_impact AS CHAR), generated_at
                  FROM hospital.decision_cards WHERE id=?1 AND branch_id=?2
                """)
                .setParameter(1, cardId)
                .setParameter(2, branchId)
                .getResultList();
        if (rows.isEmpty()) throw BusinessException.notFound("Decision card");
        return toDecisionCard(rows.get(0));
    }

    private DecisionCardResponse toDecisionCard(Object[] row) {
        DecisionCardResponse card = new DecisionCardResponse();
        card.id = DbValues.uuid(row[0]);
        card.cardCode = (String) row[1];
        card.category = (String) row[2];
        card.severity = (String) row[3];
        card.status = (String) row[4];
        card.title = (String) row[5];
        card.problemStatement = (String) row[6];
        card.analysisSummary = (String) row[7];
        card.recommendation = (String) row[8];
        card.evidenceWindowStart = toInstant(row[9]);
        card.evidenceWindowEnd = toInstant(row[10]);
        card.observedValue = row[11] == null ? null : new BigDecimal(row[11].toString());
        card.thresholdValue = row[12] == null ? null : new BigDecimal(row[12].toString());
        card.unit = (String) row[13];
        card.confidencePercent = row[14] == null ? null : new BigDecimal(row[14].toString());
        card.assumptionsJson = row[15] == null ? "[]" : row[15].toString();
        card.expectedImpactJson = row[16] == null ? "{}" : row[16].toString();
        card.generatedAt = toInstant(row[17]);
        return card;
    }

    private void requireManagementRole() {
        CurrentUser actor = users.requireCurrentUser();
        if (!hasAnyRole(actor, "HOSPITAL_ADMIN", "HOSPITAL_MANAGER")) {
            throw BusinessException.forbidden("DECISION_CARD_MANAGEMENT_REQUIRED", "Only hospital administrators and managers may manage decision cards.");
        }
    }

    private boolean hasAnyRole(CurrentUser user, String... roles) {
        for (String role : roles) if (user.roleCodes().contains(role)) return true;
        return false;
    }

    private boolean allowedTransition(String oldStatus, String target) {
        return switch (oldStatus) {
            case "OPEN" -> List.of("ACKNOWLEDGED", "IN_PROGRESS", "DISMISSED").contains(target);
            case "ACKNOWLEDGED" -> List.of("IN_PROGRESS", "COMPLETED", "DISMISSED").contains(target);
            case "IN_PROGRESS" -> List.of("COMPLETED", "DISMISSED").contains(target);
            default -> false;
        };
    }

    private Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unsupported MySQL timestamp result type: " + value.getClass().getName());
    }

    private ZoneId safeZone(String value) {
        try {
            return ZoneId.of(value);
        } catch (Exception exception) {
            return ZoneId.of("UTC");
        }
    }
}

package com.smarthospital.laboratory.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "lab_results")
public class LabResult {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_order_item_id", nullable = false, unique = true, updatable = false)
    private LabOrderItem labOrderItem;

    @Column(name = "result_text", columnDefinition = "text")
    private String resultText;

    @Column(name = "result_numeric", precision = 16, scale = 5)
    private BigDecimal resultNumeric;

    @Column(name = "result_unit", length = 40)
    private String resultUnit;

    @Column(name = "reference_range", length = 120)
    private String referenceRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "abnormal_flag", nullable = false, length = 12)
    private AbnormalFlag abnormalFlag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private StaffMember performedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private StaffMember reviewedBy;

    @Column(name = "performed_at")
    private Instant performedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected LabResult() { }

    public static LabResult record(
            LabOrderItem item,
            String resultText,
            BigDecimal resultNumeric,
            String resultUnit,
            String referenceRange,
            AbnormalFlag abnormalFlag,
            StaffMember performer) {
        LabResult result = new LabResult();
        result.labOrderItem = item;
        result.resultText = resultText;
        result.resultNumeric = resultNumeric;
        result.resultUnit = resultUnit;
        result.referenceRange = referenceRange;
        result.abnormalFlag = abnormalFlag == null ? AbnormalFlag.UNKNOWN : abnormalFlag;
        result.performedBy = performer;
        result.performedAt = Instant.now();
        return result;
    }

    public void reviewAndRelease(StaffMember reviewer) {
        if (releasedAt != null || reviewedAt != null) {
            throw new IllegalStateException("The result has already been reviewed or released.");
        }
        if (performedBy != null && performedBy.getId().equals(reviewer.getId())) {
            throw new IllegalStateException("The performer cannot independently review the same result.");
        }
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        releasedAt = reviewedAt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (abnormalFlag == null) abnormalFlag = AbnormalFlag.UNKNOWN;
    }

    public UUID getId() { return id; }
    public UUID getLabOrderItemId() { return labOrderItem.getId(); }
    public String getResultText() { return resultText; }
    public BigDecimal getResultNumeric() { return resultNumeric; }
    public String getResultUnit() { return resultUnit; }
    public String getReferenceRange() { return referenceRange; }
    public AbnormalFlag getAbnormalFlag() { return abnormalFlag; }
    public UUID getPerformedBy() { return performedBy == null ? null : performedBy.getId(); }
    public UUID getReviewedBy() { return reviewedBy == null ? null : reviewedBy.getId(); }
    public Instant getPerformedAt() { return performedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getReleasedAt() { return releasedAt; }
}

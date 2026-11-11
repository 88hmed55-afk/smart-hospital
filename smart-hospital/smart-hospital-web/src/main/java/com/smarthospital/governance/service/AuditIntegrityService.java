package com.smarthospital.governance.service;

import com.smarthospital.governance.dto.AuditIntegrityReport;
import com.smarthospital.security.auth.TokenService;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Independently re-computes the audit hash chain.
 *
 * <p>Rows are ordered by following {@code prev_hash} links from the genesis value,
 * not by timestamp, so the result is independent of clock skew or ties. For every
 * row that carries a {@code hash_payload}, the stored SHA-256 digest is recomputed
 * and compared with {@code row_hash}. The final linked hash is compared with
 * {@code audit_chain_state.last_hash} and the linked row count with
 * {@code last_sequence}.</p>
 *
 * <p>Rows written before the hash chain was introduced have NULL {@code prev_hash}
 * and {@code row_hash}; they are counted as unchained and excluded from the walk.
 * Chain rows written before V019 have no {@code hash_payload}; their links are
 * checked but their digest cannot be recomputed, so they are reported as legacy.</p>
 */
@Stateless
public class AuditIntegrityService {
    private static final String GENESIS = "0".repeat(64);

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    public AuditIntegrityReport verify() {
        AuditIntegrityReport report = new AuditIntegrityReport();
        report.checkedAt = Instant.now();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, prev_hash, row_hash, hash_payload
                  FROM hospital.audit_logs
                """)
                .getResultList();

        report.totalRows = rows.size();

        // Only rows carrying both links are part of the tamper-evident chain.
        Map<String, Object[]> byPrev = new LinkedHashMap<>();
        long unchained = 0;
        long duplicateLinks = 0;
        for (Object[] row : rows) {
            String prev = (String) row[1];
            String rowHash = (String) row[2];
            if (prev == null || rowHash == null || rowHash.isBlank()) {
                unchained++;
                continue;
            }
            if (byPrev.putIfAbsent(prev, row) != null) {
                duplicateLinks++; // fork: two rows claim the same predecessor
            }
        }
        report.unchainedRows = unchained;

        String cursor = GENESIS;
        long reached = 0;
        long brokenLinks = 0;
        long hashMismatches = 0;
        long legacy = 0;
        String lastHash = GENESIS;

        while (true) {
            Object[] row = byPrev.get(cursor);
            if (row == null) {
                break;
            }
            reached++;
            String rowHash = (String) row[2];
            String payload = (String) row[3];
            if (payload == null) {
                legacy++;
            } else if (!TokenService.sha256Hex(payload).equalsIgnoreCase(rowHash)) {
                hashMismatches++;
            }
            lastHash = rowHash;
            cursor = rowHash;
            if (reached > byPrev.size()) {
                brokenLinks++; // cycle protection
                break;
            }
        }

        long expectedChainRows = byPrev.size();
        if (reached < expectedChainRows) {
            brokenLinks += expectedChainRows - reached;
        }
        brokenLinks += duplicateLinks;

        report.legacyRows = legacy;
        report.verifiedRows = reached - legacy;
        report.hashMismatches = hashMismatches;
        report.brokenLinks = brokenLinks;
        report.recomputedHeadHash = lastHash;
        report.chainRows = reached;

        List<?> state = em.createNativeQuery(
                        "SELECT last_sequence, last_hash FROM hospital.audit_chain_state WHERE id = 1")
                .getResultList();
        if (!state.isEmpty()) {
            Object[] row = (Object[]) state.get(0);
            report.storedSequence = ((Number) row[0]).longValue();
            report.storedHeadHash = (String) row[1];
        }

        boolean headMatches = report.storedHeadHash == null
                ? reached == 0
                : report.storedHeadHash.equalsIgnoreCase(lastHash);
        boolean sequenceMatches = report.storedSequence == reached;
        report.chainIntact = report.brokenLinks == 0
                && report.hashMismatches == 0
                && headMatches
                && sequenceMatches
                && reached == expectedChainRows;

        if (report.chainIntact) {
            report.message = legacy == 0
                    ? "Audit hash chain verified end to end."
                    : "Audit hash chain is continuous; " + legacy
                        + " chain row(s) predate hash-payload persistence and cannot be recomputed.";
            if (unchained > 0 && legacy == 0) {
                report.message += " " + unchained + " pre-chain row(s) are excluded from the walk.";
            }
        } else {
            report.message = "Audit hash chain verification failed: brokenLinks=" + report.brokenLinks
                    + ", hashMismatches=" + report.hashMismatches
                    + ", headMatches=" + headMatches
                    + ", sequenceMatches=" + sequenceMatches + ".";
        }
        return report;
    }
}

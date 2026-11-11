-- V019: Persist the exact hash preimage for every audit row.
--
-- AuditService hashes a canonical pipe-delimited payload into audit_logs.row_hash.
-- Until now only the JSON metadata was stored, and MySQL normalises JSON on write
-- (key ordering/whitespace), so an independent verifier could not reconstruct the
-- original preimage. hash_payload stores the exact string that was hashed, which
-- makes the tamper-evident chain independently verifiable.
--
-- It is additive and nullable: rows written before this migration keep NULL and are
-- reported as legacy by the verifier. Production deployments created from V001..V019
-- have no legacy rows.
USE hospital;

ALTER TABLE audit_logs
    ADD COLUMN hash_payload TEXT NULL AFTER row_hash;

-- V017: Decouple the audit trail from foreign-key lock ordering.
--
-- Audit rows must be appendable even when the referenced account or branch row is
-- locked by the transaction being audited, and must survive later deletion of the
-- referenced record. The identifiers are retained as immutable references; the
-- hash chain provides integrity. Foreign keys here caused lock-wait deadlocks
-- between REQUIRES_NEW security events and the business transaction.
USE hospital;

ALTER TABLE audit_logs DROP FOREIGN KEY fk_audit_logs_actor_user_id_72;
ALTER TABLE audit_logs DROP FOREIGN KEY fk_audit_logs_branch_id_73;

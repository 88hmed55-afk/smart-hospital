-- V021: repair JSON type checks introduced by V002.
--
-- V002 compared JSON_TYPE(...) with lowercase literals ('object', 'array').
-- JSON_TYPE() returns uppercase names and, on MySQL 8.0, the comparison resolves
-- to a case-sensitive collation, so every valid JSON object/array was rejected
-- with "Check constraint ... is violated". The checks are recreated with the
-- uppercase names that JSON_TYPE() actually returns.

ALTER TABLE metric_snapshots
    DROP CHECK ck_metric_dimensions_object,
    ADD CONSTRAINT ck_metric_dimensions_object
        CHECK (JSON_TYPE(dimensions) = 'OBJECT');

ALTER TABLE decision_cards
    DROP CHECK ck_decision_card_json,
    ADD CONSTRAINT ck_decision_card_json
        CHECK (JSON_TYPE(assumptions) = 'ARRAY' AND JSON_TYPE(expected_impact) = 'OBJECT');

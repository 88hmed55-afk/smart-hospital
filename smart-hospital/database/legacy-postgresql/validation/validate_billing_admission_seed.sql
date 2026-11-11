\set ON_ERROR_STOP on
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM hospital.wards WHERE ward_code='GEN-WARD-A') THEN RAISE EXCEPTION 'Synthetic ward fixture missing'; END IF;
 IF (SELECT count(*) FROM hospital.beds b JOIN hospital.rooms r ON r.id=b.room_id WHERE r.ward_id='50000000-0000-4000-8000-000000000001') < 3 THEN RAISE EXCEPTION 'Synthetic beds fixture incomplete'; END IF;
 IF (SELECT count(*) FROM hospital.service_catalog WHERE code IN ('CONS-GEN','LAB-CBC','XR-CHEST','PROC-DRESS','ROOM-GEN')) <> 5 THEN RAISE EXCEPTION 'Synthetic service catalogue incomplete'; END IF;
END $$;
SELECT 'V007 development fixtures validation passed' AS result;

'use strict';

/*
 * Inpatient stays. Each admission owns a dedicated INPATIENT encounter plus the
 * same clinical support used by outpatient visits. Bed assignments respect the
 * "one active admission per patient" and "one current bed per admission" rules.
 */

const {
  BRANCH_ID, DEPT, USER, NOW, WINDOW_START,
  txId, chance, pick, int, weighted, shuffle,
  addMinutes, addHours, addDays, sqlDate, workingInstant, localDate,
  DISCHARGE_SUMMARIES
} = require('./util');
const clinical = require('./clinical');

const ADMISSION_REASONS = [
  'التهاب رئوي حاد يحتاج مراقبة وعلاجاً وريدياً',
  'ألم صدري يستدعي المراقبة القلبية',
  'التهاب معوي حاد مع جفاف',
  'مضاعفات مرض السكري',
  'التهاب زائدة دودية حاد',
  'متابعة قصور كلوي مزمن',
  'ولادة ومتابعة ما بعد الولادة',
  'إصابة وكسر يحتاج تثبيتاً ومراقبة',
  'ارتفاع ضغط الدم غير المنضبط',
  'ضيق تنفس حاد'
];

const SEVERE_CODES = ['J18.9', 'J44.9', 'K35.8', 'I48.91', 'N18.9', 'E11.9', 'I10', 'S52.5', 'A09'];

function wardTypeByRoom(ctx) {
  const wardById = {};
  for (const ward of ctx.ref.wards) wardById[ward.id] = ward.type;
  const map = {};
  for (const room of ctx.ref.rooms) map[room.id] = wardById[room.wardId] || 'GENERAL';
  return map;
}

function generate(ctx, total, activeCount) {
  const bedWard = wardTypeByRoom(ctx);
  const usableBeds = ctx.ref.beds.filter((bed) => bed.status !== 'OUT_OF_SERVICE');
  const activeBeds = shuffle(usableBeds).slice(0, Math.min(activeCount, usableBeds.length));
  const admissions = [];
  const assignments = [];
  const today = NOW;

  for (let index = 0; index < total; index += 1) {
    const isActive = index < activeBeds.length;
    const patient = pick(ctx.patients);
    const doctor = pick(clinical.doctorsIn(ctx, DEPT.INPATIENT));

    let admittedAt;
    let dischargedAt = null;
    let bed;
    if (isActive) {
      bed = activeBeds[index];
      admittedAt = addHours(today, -int(6, 24 * 9));
    } else {
      bed = pick(usableBeds);
      admittedAt = workingInstant(addDays(today, -int(3, ctx.windowDays - 3)), 6, 21);
      const stayDays = weighted([[1, 18], [2, 24], [3, 22], [4, 16], [5, 10], [6, 6], [7, 2], [9, 2]]);
      dischargedAt = addDays(admittedAt, stayDays);
      if (dischargedAt.getTime() > today.getTime() - 3600000) dischargedAt = new Date(today.getTime() - 3600000);
    }

    const isMaternity = !isActive && patient.sex === 'FEMALE' && patient.age >= 18 && patient.age <= 45 && chance(0.22);
    const primaryCode = isMaternity ? 'O80' : pick(SEVERE_CODES);
    const admissionId = txId();
    const encounterId = txId();
    const admissionType = weighted([['EMERGENCY', 55], ['ELECTIVE', 30], ['OBSERVATION', 10], ['TRANSFER', 5]]);

    const encounter = {
      id: encounterId, branch_id: BRANCH_ID, patient_id: patient.id, appointment_id: null,
      department_id: DEPT.INPATIENT, attending_doctor_id: doctor.staffId,
      encounter_type: 'INPATIENT', status: isActive ? 'IN_PROGRESS' : 'COMPLETED',
      chief_complaint: pick(ADMISSION_REASONS),
      started_at: admittedAt, ended_at: dischargedAt,
      created_by: null, version: 0, patient, doctor, age: patient.age, sex: patient.sex
    };
    ctx.encounters.push(encounter);
    clinical.addEncounterSupport(ctx, encounter, {
      profile: isMaternity ? 'MATERNITY' : null, primaryCode
    });

    const disposition = isActive ? null : weighted([
      ['HOME', 82], ['TRANSFERRED', 8], ['AMA', 6], ['DECEASED', 2], ['OTHER', 2]
    ]);
    const admission = {
      id: admissionId, branch_id: BRANCH_ID,
      admission_number: 'ADM-' + localDate(admittedAt).replace(/-/g, '') + '-' + String(1000 + index).slice(1),
      patient_id: patient.id, encounter_id: encounterId, attending_doctor_id: doctor.staffId,
      admission_type: admissionType,
      admission_status: isActive ? 'ADMITTED' : 'DISCHARGED',
      admission_reason: encounter.chief_complaint,
      admitted_at: admittedAt, admitted_by: USER.RECEPTION,
      discharged_at: dischargedAt, discharged_by: isActive ? null : USER.DOCTOR1,
      discharge_disposition: disposition,
      discharge_summary: isActive ? null : pick(DISCHARGE_SUMMARIES),
      created_at: admittedAt, updated_at: dischargedAt || admittedAt, version: 0
    };
    admissions.push(admission);

    assignments.push({
      id: txId(), branch_id: BRANCH_ID, admission_id: admissionId, bed_id: bed.id,
      assignment_status: isActive ? 'ACTIVE' : 'COMPLETED',
      started_at: admittedAt, ended_at: dischargedAt,
      assigned_by: USER.NURSE, released_by: isActive ? null : USER.NURSE,
      transfer_reason: null,
      created_at: admittedAt
    });

    // A small share of stays experience one bed transfer.
    if (!isActive && chance(0.08)) {
      const firstEnd = addDays(admittedAt, int(1, 2));
      if (firstEnd.getTime() < dischargedAt.getTime() - 3600000) {
        assignments[assignments.length - 1].ended_at = firstEnd;
        assignments[assignments.length - 1].assignment_status = 'COMPLETED';
        assignments[assignments.length - 1].released_by = USER.NURSE;
        const otherBed = pick(usableBeds.filter((candidate) => candidate.id !== bed.id));
        assignments.push({
          id: txId(), branch_id: BRANCH_ID, admission_id: admissionId, bed_id: otherBed.id,
          assignment_status: 'COMPLETED', started_at: firstEnd, ended_at: dischargedAt,
          assigned_by: USER.NURSE, released_by: USER.NURSE,
          transfer_reason: 'نقل داخلي لتحسين مستوى المراقبة',
          created_at: firstEnd
        });
      }
    }
  }

  ctx.admissions = admissions;
  ctx.bedAssignments = assignments;
  return { admissions, assignments };
}

function insert(db, ctx) {
  db.insert('admissions',
    ['id', 'branch_id', 'admission_number', 'patient_id', 'encounter_id', 'attending_doctor_id',
      'admission_type', 'admission_status', 'admission_reason', 'admitted_at', 'admitted_by',
      'discharged_at', 'discharged_by', 'discharge_disposition', 'discharge_summary',
      'created_at', 'updated_at', 'version'],
    ctx.admissions.map((row) => ({
      id: row.id, branch_id: row.branch_id, admission_number: row.admission_number,
      patient_id: row.patient_id, encounter_id: row.encounter_id,
      attending_doctor_id: row.attending_doctor_id, admission_type: row.admission_type,
      admission_status: row.admission_status, admission_reason: row.admission_reason,
      admitted_at: row.admitted_at, admitted_by: row.admitted_by,
      discharged_at: row.discharged_at, discharged_by: row.discharged_by,
      discharge_disposition: row.discharge_disposition, discharge_summary: row.discharge_summary,
      created_at: row.created_at, updated_at: row.updated_at, version: 0
    })), 300);
  db.insert('bed_assignments',
    ['id', 'branch_id', 'admission_id', 'bed_id', 'assignment_status', 'started_at', 'ended_at',
      'assigned_by', 'released_by', 'transfer_reason', 'created_at'],
    ctx.bedAssignments, 300);
  db.log('  admissions: ' + ctx.admissions.length + ' (' +
    ctx.admissions.filter((admission) => admission.admission_status === 'ADMITTED').length +
    ' active), bed assignments: ' + ctx.bedAssignments.length);
}

module.exports = { generate, insert };

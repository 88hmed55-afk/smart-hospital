'use strict';

/*
 * Outpatient clinical activity: appointments, encounters and the records that
 * hang off an encounter (vitals, diagnoses, notes, care team, prescriptions).
 * `addEncounterSupport` is also reused by the admissions module for inpatient
 * stays so every encounter has a complete clinical picture.
 */

const {
  BRANCH_ID, DEPT, USER, STAFF, NOW, WINDOW_START, FUTURE_DAYS,
  txId, chance, pick, int, dec, weighted, shuffle,
  addMinutes, addDays, dayStartLocal, workingInstant, eachDay, localDateParts,
  COMPLAINTS, APPOINTMENT_REASONS
} = require('./util');

function doctorsIn(ctx, departmentId) {
  const list = ctx.ref.doctors.filter((doctor) => doctor.departmentId === departmentId);
  return list.length ? list : ctx.ref.doctors;
}

function pickDoctor(ctx, departmentId) {
  const list = doctorsIn(ctx, departmentId);
  // The seeded demo doctor is used for a minority of visits; the richer Arabic
  // profiles carry most of the six-month history.
  if (departmentId === DEPT.GEN && list.length > 1 && chance(0.2)) {
    const demo = list.find((doctor) => doctor.firstName === 'Demo');
    if (demo) return demo;
  }
  return pick(list);
}

function nurseFor(ctx, departmentId) {
  if (departmentId === DEPT.ER && ctx.newStaff.nurse4) return ctx.newStaff.nurse4;
  if (departmentId === DEPT.INPATIENT) {
    return pick([STAFF.NURSE1, ctx.newStaff.nurse2, ctx.newStaff.nurse3].filter(Boolean));
  }
  return pick([STAFF.NURSE1, ctx.newStaff.nurse2].filter(Boolean));
}

const DIAGNOSIS_PROFILES = {
  CHILD: ['J06.9', 'A09', 'R50.9', 'J45.909', 'D50.9', 'H10.9', 'N39.0'],
  ADULT: ['J06.9', 'K29.7', 'M54.5', 'G43.9', 'N39.0', 'A09', 'R50.9', 'E78.5', 'I10', 'E11.9', 'J45.909'],
  SENIOR: ['I10', 'E11.9', 'E78.5', 'I48.91', 'N18.9', 'J44.9', 'M54.5', 'J18.9', 'D50.9'],
  ER: ['S52.5', 'J18.9', 'A09', 'N39.0', 'R50.9', 'L03.9', 'H10.9', 'K35.8'],
  MATERNITY: ['O80', 'D50.9', 'N39.0', 'R50.9']
};

function diagnosisPool(ctx, encounter, profile) {
  let codes;
  if (profile === 'MATERNITY') codes = DIAGNOSIS_PROFILES.MATERNITY;
  else if (encounter.departmentId === DEPT.ER) codes = DIAGNOSIS_PROFILES.ER;
  else if (encounter.age < 13) codes = DIAGNOSIS_PROFILES.CHILD;
  else if (encounter.age >= 60) codes = DIAGNOSIS_PROFILES.SENIOR;
  else codes = DIAGNOSIS_PROFILES.ADULT;
  const wanted = new Set(codes);
  const matches = ctx.ref.diagnoses.filter((diagnosis) => wanted.has(diagnosis.code));
  return matches.length ? matches : ctx.ref.diagnoses;
}

const COMMON_MED_CODES = [
  'DEMO2-MED-001', 'DEMO2-MED-002', 'DEMO2-MED-006', 'DEMO2-MED-008', 'DEMO2-MED-009',
  'DEMO2-MED-010', 'DEMO2-MED-011', 'DEMO2-MED-012', 'DEMO2-MED-013', 'DEMO2-MED-014',
  'DEMO2-MED-016', 'DEMO2-MED-017', 'DEMO2-MED-018', 'DEMO2-MED-019', 'DEMO2-MED-020',
  'DEMO2-MED-021', 'DEMO2-MED-003', 'DEMO2-MED-004', 'DEMO2-MED-005', 'DEMO2-MED-007',
  'DEMO2-MED-015', 'DEMO2-MED-022'
];

function pickMedications(ctx, count) {
  const available = ctx.ref.medications.filter((medication) => COMMON_MED_CODES.includes(medication.code));
  return shuffle(available.length ? available : ctx.ref.medications).slice(0, count);
}

function vitalsFor(ctx, encounter, nurseId) {
  const isChild = encounter.age < 13;
  const measuredAt = addMinutes(encounter.startedAt, int(5, 18));
  const febrile = chance(0.16);
  const systolic = isChild ? int(90, 118) : int(102, 165);
  const diastolic = Math.max(50, Math.min(systolic - 20, isChild ? int(58, 76) : int(62, 98)));
  const weight = isChild ? dec(8, 42, 1) : dec(45, 112, 1);
  const height = isChild ? dec(65, 155, 1) : dec(150, 190, 1);
  return {
    id: txId(), encounter_id: encounter.id, measured_by: nurseId, measured_at: measuredAt,
    temperature_c: febrile ? dec(38.1, 39.4, 1) : dec(36.2, 37.4, 1),
    systolic_mmhg: systolic, diastolic_mmhg: diastolic, pulse_bpm: int(58, 108),
    respiratory_rate: isChild ? int(18, 28) : int(12, 22),
    oxygen_saturation: chance(0.12) ? dec(90, 94.9, 2) : dec(95, 100, 2),
    weight_kg: weight, height_cm: height,
    notes: chance(0.1) ? 'تم القياس في وضع الجلوس بعد راحة قصيرة.' : null
  };
}

function noteText(type, encounter, diagnosis) {
  const complaint = encounter.chiefComplaint || 'شكوى عامة';
  switch (type) {
    case 'HISTORY':
      return 'يشكو المريض من ' + complaint + '. لا توجد حساسية دوائية معروفة. التاريخ المرضي مستقر ولا توجد أعراض إنذارية حالياً.';
    case 'EXAMINATION':
      return 'الفحص العام: المريض واعٍ ومتجاوب، العلامات الحيوية مستقرة. الصدر نظيف التنفس، القلب منتظم، البطن لين غير مؤلم.';
    case 'ASSESSMENT':
      return 'التقييم السريري يتوافق مع ' + (diagnosis ? diagnosis.description : 'حالة مستقرة') + '. لا توجد علامات خطورة تستدعي التنويم.';
    case 'PLAN':
      return 'الخطة: علاج تحفظي، تعديل نمط الحياة، متابعة بعد أسبوع أو عند تدهور الأعراض. تم شرح تعليمات الدواء للمريض.';
    case 'PROGRESS':
      return 'تحسن تدريجي في الأعراض، العلامات الحيوية ضمن الحدود الطبيعية، الاستمرار على الخطة العلاجية الحالية.';
    case 'DISCHARGE':
      return 'الحالة مستقرة عند التسريح، مع توصية بالمتابعة في العيادة الخارجية وإكمال العلاج المنزلي.';
    default:
      return 'ملاحظة سريرية: ' + complaint + '.';
  }
}

function prescriptionItems(ctx, prescriptionId) {
  const meds = pickMedications(ctx, int(1, 3));
  return meds.map((medication) => {
    const form = (medication.form || '').toUpperCase();
    let quantity = int(10, 30);
    let dose = 'قرص واحد';
    let frequency = pick(['مرة يومياً', 'مرتين يومياً', 'ثلاث مرات يومياً', 'عند الحاجة', 'كل 8 ساعات']);
    if (form.includes('INHALER')) { quantity = int(1, 2); dose = 'بخة واحدة'; frequency = 'عند الحاجة'; }
    if (form.includes('INJECTION')) { quantity = int(1, 5); dose = 'حقنة واحدة'; frequency = 'مرة يومياً'; }
    if (form.includes('PEN')) { quantity = int(1, 3); dose = 'وحدة واحدة'; frequency = 'مرة يومياً'; }
    return {
      id: txId(), prescription_id: prescriptionId, medication_id: medication.id,
      dose, route: medication.route || 'ORAL', frequency, duration_days: int(3, 14),
      quantity_prescribed: quantity,
      instructions: pick(['بعد الأكل', 'قبل النوم', 'مع كوب ماء', 'عند اللزوم', 'قبل الأكل بنصف ساعة'])
    };
  });
}

function addEncounterSupport(ctx, rawEncounter, options) {
  const opts = Object.assign({
    vitals: true, diagnoses: true, notes: true, careTeam: true,
    prescription: true, lab: true, profile: null, primaryCode: null
  }, options || {});
  const store = ctx.clinical;
  // Callers build encounters from database column names; the helpers below use
  // camelCase aliases, so normalise once here for every consumer.
  const encounter = Object.assign({}, rawEncounter, {
    startedAt: rawEncounter.startedAt || rawEncounter.started_at,
    endedAt: rawEncounter.endedAt !== undefined ? rawEncounter.endedAt : rawEncounter.ended_at,
    departmentId: rawEncounter.departmentId || rawEncounter.department_id,
    patientId: rawEncounter.patientId || rawEncounter.patient_id,
    chiefComplaint: rawEncounter.chiefComplaint || rawEncounter.chief_complaint
  });
  const nurseId = nurseFor(ctx, encounter.departmentId);

  if (opts.vitals && chance(0.94)) {
    store.vitals.push(vitalsFor(ctx, encounter, nurseId));
    if (chance(0.12)) {
      const second = vitalsFor(ctx, encounter, nurseId);
      let measuredAt = addMinutes(encounter.startedAt, int(70, 220));
      if (measuredAt.getTime() > NOW.getTime() - 60000) measuredAt = addMinutes(NOW, -1);
      if (measuredAt.getTime() <= second.measured_at.getTime()) {
        measuredAt = addMinutes(second.measured_at, 5);
      }
      second.measured_at = measuredAt;
      second.temperature_c = dec(36.4, 37.8, 1);
      store.vitals.push(second);
    }
  }

  const pool = diagnosisPool(ctx, encounter, opts.profile);
  let primary = null;
  if (opts.primaryCode) {
    primary = ctx.ref.diagnoses.find((diagnosis) => diagnosis.code === opts.primaryCode) || null;
  }
  if (!primary && opts.diagnoses) primary = pick(pool);
  if (opts.diagnoses && primary) {
    store.diagnoses.push({
      id: txId(), encounter_id: encounter.id, diagnosis_id: primary.id, recorded_by: encounter.doctor.staffId,
      is_primary: 1, clinical_status: chance(0.82) ? 'CONFIRMED' : 'PROVISIONAL',
      recorded_at: addMinutes(encounter.startedAt, int(15, 45))
    });
    if (chance(0.38)) {
      const secondary = pick(pool.filter((diagnosis) => diagnosis.id !== primary.id));
      if (secondary) {
        store.diagnoses.push({
          id: txId(), encounter_id: encounter.id, diagnosis_id: secondary.id, recorded_by: encounter.doctor.staffId,
          is_primary: 0, clinical_status: chance(0.5) ? 'PROVISIONAL' : 'CONFIRMED',
          recorded_at: addMinutes(encounter.startedAt, int(20, 55))
        });
      }
    }
  }

  if (opts.notes && chance(0.9)) {
    const firstType = pick(['HISTORY', 'EXAMINATION', 'PROGRESS']);
    const secondType = pick(['ASSESSMENT', 'PLAN']);
    const signed = chance(0.9);
    const signedAt = addMinutes(encounter.startedAt, int(25, 70));
    store.notes.push({
      id: txId(), encounter_id: encounter.id, author_staff_id: encounter.doctor.staffId,
      note_type: firstType, note_text: noteText(firstType, encounter, primary),
      note_status: signed ? 'SIGNED' : 'DRAFT', created_at: addMinutes(encounter.startedAt, int(10, 30)),
      signed_at: signed ? signedAt : null, amended_from_id: null, version: 0
    });
    if (chance(0.72)) {
      const secondSigned = chance(0.9);
      store.notes.push({
        id: txId(), encounter_id: encounter.id, author_staff_id: encounter.doctor.staffId,
        note_type: secondType, note_text: noteText(secondType, encounter, primary),
        note_status: secondSigned ? 'SIGNED' : 'DRAFT',
        created_at: addMinutes(encounter.startedAt, int(20, 50)),
        signed_at: secondSigned ? addMinutes(encounter.startedAt, int(30, 80)) : null,
        amended_from_id: null, version: 0
      });
    }
  }

  if (opts.careTeam && chance(0.92)) {
    store.careTeam.push({
      id: txId(), encounter_id: encounter.id, staff_id: nurseId, care_role: 'PRIMARY_NURSE',
      assigned_by: USER.NURSE, assigned_at: addMinutes(encounter.startedAt, -int(5, 20)),
      released_at: encounter.endedAt
    });
  }

  if (opts.prescription && chance(0.63)) {
    const cancelled = chance(0.02);
    const prescriptionId = txId();
    const orderedAt = addMinutes(encounter.endedAt || encounter.startedAt, int(-10, 5));
    store.prescriptions.push({
      id: prescriptionId, encounter_id: encounter.id, prescriber_staff_id: encounter.doctor.staffId,
      status: cancelled ? 'CANCELLED' : 'ACTIVE', ordered_at: orderedAt,
      cancelled_at: cancelled ? addMinutes(orderedAt, int(30, 180)) : null,
      cancelled_by: cancelled ? USER.DOCTOR1 : null, branch_id: BRANCH_ID, version: 0,
      items: []
    });
    const items = prescriptionItems(ctx, prescriptionId);
    for (const item of items) {
      store.prescriptionItems.push(item);
      store.prescriptions[store.prescriptions.length - 1].items.push(item);
    }
  }

  if (opts.lab && chance(0.46)) {
    const tests = shuffle(ctx.ref.labTests).slice(0, int(1, 3));
    const priority = weighted([['ROUTINE', 88], ['URGENT', 10], ['STAT', 2]]);
    const orderedAt = addMinutes(encounter.startedAt, int(15, 50));
    const orderId = txId();
    const order = {
      id: orderId, encounter_id: encounter.id, ordered_by: encounter.doctor.staffId,
      priority, status: 'ORDERED', clinical_question: pick([
        'تقييم وظائف الكلى', 'متابعة السكر التراكمي', 'استبعاد التهاب', 'فحص شامل',
        'تقييم فقر الدم', 'متابعة ضغط الدم', 'تقييم وظائف الكبد', 'متابعة الدهون'
      ]),
      ordered_at: orderedAt.getTime() > NOW.getTime() - 60000 ? addMinutes(NOW, -int(1, 5)) : orderedAt,
      branch_id: BRANCH_ID,
      patientId: encounter.patientId, departmentId: encounter.departmentId,
      items: tests.map((test) => ({
        id: txId(), lab_order_id: orderId, lab_test_id: test.id, test,
        status: 'PENDING', specimen_code: null, collected_at: null, collected_by: null
      }))
    };
    store.labOrders.push(order);
  }
  return encounter;
}

function weekdayOf(day) {
  const p = localDateParts(day);
  return new Date(Date.UTC(p.year, p.month - 1, p.day)).getUTCDay();
}

function generate(ctx) {
  const appointments = [];
  const encounters = [];
  ctx.clinical = {
    vitals: [], diagnoses: [], notes: [], careTeam: [],
    prescriptions: [], prescriptionItems: [], labOrders: []
  };

  const today = dayStartLocal(NOW);
  const days = eachDay(addDays(NOW, -ctx.windowDays), addDays(NOW, FUTURE_DAYS));

  for (const day of days) {
    const dow = weekdayOf(day);
    const isFuture = day.getTime() > today.getTime();
    const isToday = day.getTime() === today.getTime();
    let count;
    if (dow === 5) count = int(2, 5);
    else if (dow === 6) count = int(5, 9);
    else count = int(10, 16);
    if (isFuture) count = Math.max(2, Math.round(count * 0.6));

    for (let i = 0; i < count; i += 1) {
      const department = weighted([[DEPT.GEN, 68], [DEPT.ER, 16], [DEPT.INPATIENT, 8], [DEPT.GEN, 8]]);
      const doctor = pickDoctor(ctx, department);
      const start = workingInstant(day, 8, 17);
      const duration = weighted([[20, 35], [30, 50], [45, 15]]);
      // Anything that has not finished yet is still a booking, including the
      // remaining slots of the current day when the seed runs in the morning.
      const pending = isFuture || start.getTime() > NOW.getTime() - 120 * 60000;
      const status = pending
        ? weighted([['BOOKED', 56], ['CONFIRMED', 32], ['CANCELLED', 12]])
        : weighted([['COMPLETED', 86], ['CANCELLED', 6], ['NO_SHOW', 5], ['BOOKED', 3]]);
      const patient = pick(ctx.patients);
      const appointment = {
        id: txId(), branch_id: BRANCH_ID, patient_id: patient.id, doctor_staff_id: doctor.staffId,
        department_id: department, starts_at: start, ends_at: addMinutes(start, duration),
        reason: pick(APPOINTMENT_REASONS), status,
        created_by: USER.RECEPTION, created_at: addDays(start, -int(1, 12)),
        updated_at: addDays(start, -int(0, 1)), version: 0,
        patient, doctor
      };
      appointments.push(appointment);

      if (status === 'COMPLETED' && chance(0.9)) {
        const startedAt = addMinutes(start, int(-4, 24));
        let endedAt = addMinutes(startedAt, int(20, 70));
        if (endedAt.getTime() > NOW.getTime()) endedAt = addMinutes(NOW, -1);
        const encounter = {
          id: txId(), branch_id: BRANCH_ID, patient_id: patient.id, appointment_id: appointment.id,
          department_id: department, attending_doctor_id: doctor.staffId,
          encounter_type: department === DEPT.ER ? 'EMERGENCY' : (chance(0.12) ? 'FOLLOW_UP' : 'OUTPATIENT'),
          status: 'COMPLETED', chief_complaint: pick(COMPLAINTS),
          started_at: startedAt, ended_at: endedAt,
          created_by: null, version: 0,
          patient, doctor, age: patient.age, sex: patient.sex
        };
        encounters.push(addEncounterSupport(ctx, encounter));
      }
    }
  }

  // Walk-ins and same-day activity keep the ER and dashboard populated.
  const walkInCount = 150;
  for (let i = 0; i < walkInCount; i += 1) {
    const day = addDays(today, -int(0, ctx.windowDays - 2));
    let startedAt = workingInstant(day, 8, 20);
    // Walk-ins that have not finished cannot exist yet: move them a few hours
    // back, inside the previous day when the seed runs just after midnight.
    const latestStart = addMinutes(NOW, -30);
    if (startedAt.getTime() > latestStart.getTime()) {
      startedAt = addMinutes(latestStart, -int(0, 240));
    }
    const department = weighted([[DEPT.ER, 68], [DEPT.GEN, 32]]);
    const doctor = pickDoctor(ctx, department);
    const patient = pick(ctx.patients);
    const closed = chance(0.85);
    let endedAt = null;
    if (closed) {
      endedAt = addMinutes(startedAt, int(30, 180));
      if (endedAt.getTime() > NOW.getTime()) endedAt = addMinutes(NOW, -1);
    }
    const encounter = {
      id: txId(), branch_id: BRANCH_ID, patient_id: patient.id, appointment_id: null,
      department_id: department, attending_doctor_id: doctor.staffId,
      encounter_type: department === DEPT.ER ? 'EMERGENCY' : 'OUTPATIENT',
      status: closed ? 'COMPLETED' : (chance(0.6) ? 'IN_PROGRESS' : 'OPEN'),
      chief_complaint: pick(COMPLAINTS), started_at: startedAt,
      ended_at: endedAt,
      created_by: null, version: 0,
      patient, doctor, age: patient.age, sex: patient.sex
    };
    encounters.push(addEncounterSupport(ctx, encounter, { profile: department === DEPT.ER ? 'ER' : null }));
  }

  ctx.appointments = appointments;
  ctx.encounters = encounters;
  return { appointments, encounters };
}

function insert(db, ctx) {
  db.insert('appointments',
    ['id', 'branch_id', 'patient_id', 'doctor_staff_id', 'department_id', 'starts_at', 'ends_at', 'reason',
      'status', 'created_by', 'created_at', 'updated_at', 'version'],
    ctx.appointments.map((row) => ({
      id: row.id, branch_id: row.branch_id, patient_id: row.patient_id,
      doctor_staff_id: row.doctor_staff_id, department_id: row.department_id,
      starts_at: row.starts_at, ends_at: row.ends_at, reason: row.reason, status: row.status,
      created_by: row.created_by, created_at: row.created_at, updated_at: row.updated_at, version: 0
    })), 400);

  db.insert('encounters',
    ['id', 'branch_id', 'patient_id', 'appointment_id', 'department_id', 'attending_doctor_id',
      'encounter_type', 'status', 'chief_complaint', 'started_at', 'ended_at', 'created_by', 'version'],
    ctx.encounters.map((row) => ({
      id: row.id, branch_id: row.branch_id, patient_id: row.patient_id, appointment_id: row.appointment_id,
      department_id: row.department_id, attending_doctor_id: row.attending_doctor_id,
      encounter_type: row.encounter_type, status: row.status, chief_complaint: row.chief_complaint,
      started_at: row.started_at, ended_at: row.ended_at, created_by: row.created_by, version: 0
    })), 400);

  const store = ctx.clinical;
  db.insert('vital_signs',
    ['id', 'encounter_id', 'measured_by', 'measured_at', 'temperature_c', 'systolic_mmhg', 'diastolic_mmhg',
      'pulse_bpm', 'respiratory_rate', 'oxygen_saturation', 'weight_kg', 'height_cm', 'notes'],
    store.vitals, 400);
  db.insert('encounter_diagnoses',
    ['id', 'encounter_id', 'diagnosis_id', 'recorded_by', 'is_primary', 'clinical_status', 'recorded_at'],
    store.diagnoses, 400);
  db.insert('clinical_notes',
    ['id', 'encounter_id', 'author_staff_id', 'note_type', 'note_text', 'note_status', 'created_at',
      'signed_at', 'amended_from_id', 'version'], store.notes, 300);
  db.insert('encounter_care_team',
    ['id', 'encounter_id', 'staff_id', 'care_role', 'assigned_by', 'assigned_at', 'released_at'],
    store.careTeam, 400);
  db.insert('prescriptions',
    ['id', 'encounter_id', 'prescriber_staff_id', 'status', 'ordered_at', 'cancelled_at', 'cancelled_by',
      'branch_id', 'version'],
    store.prescriptions.map((row) => ({
      id: row.id, encounter_id: row.encounter_id, prescriber_staff_id: row.prescriber_staff_id,
      status: row.status, ordered_at: row.ordered_at, cancelled_at: row.cancelled_at,
      cancelled_by: row.cancelled_by, branch_id: row.branch_id, version: 0
    })), 400);
  db.insert('prescription_items',
    ['id', 'prescription_id', 'medication_id', 'dose', 'route', 'frequency', 'duration_days',
      'quantity_prescribed', 'instructions'], store.prescriptionItems, 400);

  db.log('  appointments: ' + ctx.appointments.length + ', encounters: ' + ctx.encounters.length +
    ', vitals: ' + store.vitals.length + ', diagnoses: ' + store.diagnoses.length +
    ', notes: ' + store.notes.length + ', care team: ' + store.careTeam.length +
    ', prescriptions: ' + store.prescriptions.length + ' (' + store.prescriptionItems.length + ' items)');
}

module.exports = { generate, insert, addEncounterSupport, nurseFor, doctorsIn, pickDoctor };

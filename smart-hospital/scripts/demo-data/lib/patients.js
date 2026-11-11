'use strict';

/*
 * Patient demographics plus the supporting rows every patient needs before any
 * clinical transaction can exist: branch access, emergency contacts, consents
 * and allergies.
 */

const {
  BRANCH_ID, USER, txId, chance, pick, int, dec, weighted,
  MALE_NAMES, FEMALE_NAMES, FAMILY_NAMES, CITIES, STREETS,
  ALLERGENS, REACTIONS, WINDOW_START, NOW, addDays, addHours,
  workingInstant, localDate, sqlDateTime
} = require('./util');

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

function randomName(sex) {
  return {
    first: sex === 'MALE' ? pick(MALE_NAMES) : pick(FEMALE_NAMES),
    middle: sex === 'MALE' ? pick(MALE_NAMES) : pick(FEMALE_NAMES),
    last: pick(FAMILY_NAMES)
  };
}

function randomAgeGroup() {
  const group = weighted([
    ['child', 12], ['teen', 6], ['young', 26], ['adult', 34], ['senior', 22]
  ]);
  const age = {
    child: int(1, 12), teen: int(13, 17), young: int(18, 39), adult: int(40, 64), senior: int(65, 88)
  }[group];
  return age;
}

function buildPatients(count) {
  const patients = [];
  const base = addDays(WINDOW_START, -420).getTime();
  const span = NOW.getTime() - base - 86400000;
  for (let index = 0; index < count; index += 1) {
    const id = txId();
    const sex = chance(0.51) ? 'FEMALE' : 'MALE';
    const name = randomName(sex);
    const age = randomAgeGroup();
    const createdAt = new Date(base + Math.floor((index / count) * span * 0.55 + Math.random() * span * 0.45));
    const birth = new Date(Date.UTC(
      createdAt.getUTCFullYear() - age,
      int(0, 11), int(1, 28), 12, 0, 0));
    const dateOfBirth = birth.toISOString().slice(0, 10);
    const mrnDate = localDate(createdAt).replace(/-/g, '');
    const mrn = 'SH-' + mrnDate + '-' + (0x10000 + index).toString(16).toUpperCase();
    const city = pick(CITIES);
    const status = chance(0.03) ? 'INACTIVE' : 'ACTIVE';
    patients.push({
      id,
      mrn,
      national_id: String(100000000000 + index * 37 + int(1, 30)),
      national_id_type: 'YEMENI_ID',
      first_name: name.first,
      middle_name: name.middle,
      last_name: name.last,
      date_of_birth: dateOfBirth,
      sex,
      blood_group: pick(BLOOD_GROUPS),
      primary_phone: '+9677' + int(10000000, 99999999),
      email: chance(0.42) ? ('patient' + (index + 1) + '@example.ye') : null,
      address_text: city + ' - ' + pick(STREETS) + ' - مبنى ' + int(2, 90),
      preferred_language: 'ar',
      patient_status: status,
      created_at: createdAt,
      updated_at: createdAt,
      created_by: USER.RECEPTION,
      version: 0,
      age,
      city
    });
  }
  return patients;
}

function seed(db, ctx, count) {
  const patients = buildPatients(count);
  ctx.patients = patients;

  db.insert('patients',
    ['id', 'mrn', 'national_id', 'national_id_type', 'first_name', 'middle_name', 'last_name', 'date_of_birth',
      'sex', 'blood_group', 'primary_phone', 'email', 'address_text', 'preferred_language', 'patient_status',
      'created_at', 'updated_at', 'created_by', 'version'],
    patients.map((patient) => ({
      id: patient.id, mrn: patient.mrn, national_id: patient.national_id,
      national_id_type: patient.national_id_type, first_name: patient.first_name,
      middle_name: patient.middle_name, last_name: patient.last_name,
      date_of_birth: patient.date_of_birth, sex: patient.sex, blood_group: patient.blood_group,
      primary_phone: patient.primary_phone, email: patient.email, address_text: patient.address_text,
      preferred_language: patient.preferred_language, patient_status: patient.patient_status,
      created_at: patient.created_at, updated_at: patient.updated_at,
      created_by: patient.created_by, version: patient.version
    })), 300);

  // Every patient is visible to the main branch (required by the composite
  // encounter/branch FK chain).
  const accessRows = patients.map((patient) => ({
    id: txId(), patient_id: patient.id, branch_id: BRANCH_ID, access_reason: 'CARE',
    granted_by: USER.RECEPTION, granted_at: patient.created_at, revoked_at: null
  }));
  db.insert('patient_branch_access',
    ['id', 'patient_id', 'branch_id', 'access_reason', 'granted_by', 'granted_at', 'revoked_at'], accessRows, 400);

  const contactRows = [];
  const consentRows = [];
  const allergyRows = [];
  let contactSeq = 0;

  for (const patient of patients) {
    const isMinor = patient.age < 18;
    if (chance(0.62) || isMinor) {
      contactSeq += 1;
      const relative = randomName(chance(0.5) ? 'MALE' : 'FEMALE');
      contactRows.push({
        id: txId(), patient_id: patient.id,
        contact_type: isMinor ? (chance(0.7) ? 'GUARDIAN' : 'EMERGENCY') : 'EMERGENCY',
        contact_name: relative.first + ' ' + relative.last,
        relationship: isMinor ? 'ولي الأمر' : pick(['الزوج/الزوجة', 'الأب', 'الأم', 'الأخ', 'الأخت', 'الابن', 'الابنة']),
        phone_number: '+9677' + int(10000000, 99999999),
        email: null, is_primary: 1, created_at: patient.created_at
      });
      if (chance(0.25)) {
        contactRows.push({
          id: txId(), patient_id: patient.id, contact_type: 'OTHER',
          contact_name: relative.first + ' ' + relative.last, relationship: 'قريب',
          phone_number: '+9677' + int(10000000, 99999999), email: null, is_primary: 0,
          created_at: patient.created_at
        });
      }
    }

    const grantedAt = patient.created_at;
    const consent = weighted([['GRANTED', 94], ['PENDING', 3], ['REVOKED', 2], ['EXPIRED', 1]]);
    consentRows.push({
      id: txId(), patient_id: patient.id, consent_type: 'TREATMENT', status: consent,
      granted_at: consent === 'GRANTED' ? grantedAt : null,
      revoked_at: consent === 'REVOKED' ? addDays(grantedAt, int(10, 200)) : null,
      expires_at: consent === 'EXPIRED' ? addDays(grantedAt, 180) : null,
      document_reference: 'CONSENT-' + (contactSeq + 1000), recorded_by: USER.RECEPTION,
      notes: null, created_at: grantedAt, updated_at: grantedAt
    });
    if (chance(0.72)) {
      const share = weighted([['GRANTED', 82], ['PENDING', 10], ['REVOKED', 8]]);
      consentRows.push({
        id: txId(), patient_id: patient.id, consent_type: 'DATA_SHARING', status: share,
        granted_at: share === 'GRANTED' ? addDays(grantedAt, 1) : null,
        revoked_at: null, expires_at: null, document_reference: 'SHARE-' + (contactSeq + 2000),
        recorded_by: USER.RECEPTION, notes: null, created_at: grantedAt, updated_at: grantedAt
      });
    }

    if (chance(0.2)) {
      const activeAllergens = chance(0.15) ? 2 : 1;
      for (let i = 0; i < activeAllergens; i += 1) {
        allergyRows.push({
          id: txId(), patient_id: patient.id, allergen_name: pick(ALLERGENS),
          reaction: pick(REACTIONS), severity: weighted([['MILD', 40], ['MODERATE', 35], ['SEVERE', 18], ['LIFE_THREATENING', 7]]),
          verification_status: weighted([['VERIFIED', 60], ['CONFIRMED', 20], ['UNVERIFIED', 18], ['REFUTED', 2]]),
          recorded_by: ctx.ref.doctors.length ? ctx.ref.doctors[0].staffId : null,
          recorded_at: addDays(patient.created_at, int(1, 120))
        });
      }
    }
  }

  db.insert('patient_contacts',
    ['id', 'patient_id', 'contact_type', 'contact_name', 'relationship', 'phone_number', 'email', 'is_primary', 'created_at'],
    contactRows, 400);
  db.insert('patient_consents',
    ['id', 'patient_id', 'consent_type', 'status', 'granted_at', 'revoked_at', 'expires_at',
      'document_reference', 'recorded_by', 'notes', 'created_at', 'updated_at'], consentRows, 400);
  db.insert('patient_allergies',
    ['id', 'patient_id', 'allergen_name', 'reaction', 'severity', 'verification_status', 'recorded_by', 'recorded_at'],
    allergyRows, 400);

  db.log('  patients: ' + patients.length + ', contacts: ' + contactRows.length +
    ', consents: ' + consentRows.length + ', allergies: ' + allergyRows.length);
}

module.exports = { seed };

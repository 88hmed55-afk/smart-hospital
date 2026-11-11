'use strict';

/*
 * Reference data: departments, clinical staff, wards/rooms/beds, suppliers,
 * medications, billable services, diagnosis codes, lab tests and reorder
 * policies. Existing rows are never modified; new rows use the `feedface`
 * namespace so a reset can remove them after transactional rows are gone.
 */

const {
  DEPT, USER, STAFF, BRANCH_ID, PHARMACY_LOCATION,
  refId, pick, int, dec, sqlDate, addDays, NOW, sqlJson
} = require('./util');

function load(db) {
  const ref = {};
  ref.users = {};
  for (const [id, username] of db.query("SELECT id, username FROM hospital.user_accounts")) {
    ref.users[username] = id;
  }
  ref.accounts = {};
  for (const [id, code, name, type, balance] of db.query(
    'SELECT id, account_code, account_name, account_type, normal_balance FROM hospital.chart_of_accounts')) {
    ref.accounts[code] = { id, code, name, type, balance };
  }
  ref.doctors = db.query(
    'SELECT dp.staff_id, sm.first_name, sm.last_name, dp.specialty, dp.branch_id, sm.department_id ' +
    'FROM hospital.doctor_profiles dp JOIN hospital.staff_members sm ON sm.id=dp.staff_id ' +
    'WHERE dp.is_active=1 AND sm.employment_status=\'ACTIVE\'')
    .map(([staffId, firstName, lastName, specialty, branchId, departmentId]) => ({
      staffId, firstName, lastName, specialty, branchId, departmentId
    }));
  ref.diagnoses = db.query(
    'SELECT id, code_system, diagnosis_code, description FROM hospital.diagnosis_catalog WHERE is_active=1')
    .map(([id, system, code, description]) => ({ id, system, code, description }));
  ref.labTests = db.query(
    'SELECT id, code, name, result_unit, reference_range, specimen_type, standard_price, expected_minutes ' +
    'FROM hospital.lab_test_catalog WHERE is_active=1')
    .map(([id, code, name, unit, range, specimen, price, minutes]) => ({
      id, code, name, unit, range, specimen, price: Number(price), minutes: Number(minutes || 60)
    }));
  ref.services = db.query(
    'SELECT id, code, name, service_type, standard_price FROM hospital.service_catalog WHERE is_active=1')
    .map(([id, code, name, type, price]) => ({ id, code, name, type, price: Number(price) }));
  ref.medications = db.query(
    'SELECT id, code, generic_name, brand_name, strength, dosage_form, route, dispensing_unit, is_controlled ' +
    'FROM hospital.medications WHERE is_active=1')
    .map(([id, code, generic, brand, strength, form, route, unit, controlled]) => ({
      id, code, generic, brand, strength, form, route, unit, controlled: Number(controlled) === 1
    }));
  ref.wards = db.query('SELECT id, ward_code, ward_name, ward_type FROM hospital.wards WHERE is_active=1')
    .map(([id, code, name, type]) => ({ id, code, name, type }));
  ref.rooms = db.query('SELECT id, ward_id, room_number, room_type FROM hospital.rooms WHERE is_active=1')
    .map(([id, wardId, number, type]) => ({ id, wardId, number, type }));
  ref.beds = db.query('SELECT id, room_id, bed_number, bed_type, operational_status FROM hospital.beds WHERE is_active=1')
    .map(([id, roomId, number, type, status]) => ({ id, roomId, number, type, status }));
  ref.suppliers = db.query('SELECT id, supplier_code, supplier_name FROM hospital.suppliers')
    .map(([id, code, name]) => ({ id, code, name }));
  ref.locations = db.query('SELECT id, code, name, location_type FROM hospital.inventory_locations WHERE is_active=1')
    .map(([id, code, name, type]) => ({ id, code, name, type }));
  return ref;
}

// ---------------------------------------------------------------------------
// New staff members (doctors, nurses, lab technologist, pharmacist)
// ---------------------------------------------------------------------------
const NEW_STAFF = [
  { key: 'doctor2', number: 'DEMO2-DOC-002', first: 'سامي', last: 'الحكيمي', dept: DEPT.GEN, title: 'أخصائي باطنية', specialty: 'Internal Medicine', license: 'YEM-MD-21002', qualification: 'MD, Internal Medicine' },
  { key: 'doctor3', number: 'DEMO2-DOC-003', first: 'رنا', last: 'الشامي', dept: DEPT.GEN, title: 'طبيبة أسرة', specialty: 'Family Medicine', license: 'YEM-MD-21003', qualification: 'MBBS, Family Medicine' },
  { key: 'doctor4', number: 'DEMO2-DOC-004', first: 'ماجد', last: 'العولقي', dept: DEPT.ER, title: 'طبيب طوارئ', specialty: 'Emergency Medicine', license: 'YEM-MD-21004', qualification: 'MD, Emergency Medicine' },
  { key: 'doctor5', number: 'DEMO2-DOC-005', first: 'هدى', last: 'السقاف', dept: DEPT.INPATIENT, title: 'أخصائية باطنية', specialty: 'Internal Medicine', license: 'YEM-MD-21005', qualification: 'MD, Internal Medicine' },
  { key: 'doctor6', number: 'DEMO2-DOC-006', first: 'عمر', last: 'البكري', dept: DEPT.INPATIENT, title: 'أخصائي صدرية', specialty: 'Pulmonology', license: 'YEM-MD-21006', qualification: 'MD, Pulmonology' },
  { key: 'nurse2', number: 'DEMO2-NUR-002', first: 'أمل', last: 'الجابري', dept: DEPT.INPATIENT, title: 'ممرضة أولى' },
  { key: 'nurse3', number: 'DEMO2-NUR-003', first: 'نادية', last: 'الحسني', dept: DEPT.INPATIENT, title: 'ممرضة' },
  { key: 'nurse4', number: 'DEMO2-NUR-004', first: 'سميرة', last: 'القباطي', dept: DEPT.ER, title: 'ممرضة طوارئ' },
  { key: 'labtech2', number: 'DEMO2-LAB-002', first: 'وليد', last: 'الشميري', dept: DEPT.LAB, title: 'فني مختبر' },
  { key: 'pharmacist2', number: 'DEMO2-PHM-002', first: 'لينا', last: 'الحداد', dept: DEPT.PHARMACY, title: 'صيدلانية' }
];

const NEW_MEDICATIONS = [
  { code: 'DEMO2-MED-001', generic: 'Paracetamol', brand: 'باراسيتامول', strength: '500 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 40, controlled: false },
  { code: 'DEMO2-MED-002', generic: 'Ibuprofen', brand: 'إيبوبروفين', strength: '400 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 60, controlled: false },
  { code: 'DEMO2-MED-003', generic: 'Amoxicillin', brand: 'أموكسيسيلين', strength: '500 mg', form: 'CAPSULE', route: 'ORAL', unit: 'capsule', cost: 120, controlled: false },
  { code: 'DEMO2-MED-004', generic: 'Azithromycin', brand: 'أزيثرومايسين', strength: '500 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 350, controlled: false },
  { code: 'DEMO2-MED-005', generic: 'Ceftriaxone', brand: 'سيفترياكسون', strength: '1 g', form: 'INJECTION', route: 'IV', unit: 'vial', cost: 900, controlled: false },
  { code: 'DEMO2-MED-006', generic: 'Metformin', brand: 'ميتفورمين', strength: '850 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 55, controlled: false },
  { code: 'DEMO2-MED-007', generic: 'Insulin Glargine', brand: 'إنسولين جلارجين', strength: '100 IU/mL', form: 'INJECTION', route: 'SC', unit: 'pen', cost: 6500, controlled: false },
  { code: 'DEMO2-MED-008', generic: 'Amlodipine', brand: 'أملوديبين', strength: '5 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 70, controlled: false },
  { code: 'DEMO2-MED-009', generic: 'Losartan', brand: 'لوسارتان', strength: '50 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 90, controlled: false },
  { code: 'DEMO2-MED-010', generic: 'Atorvastatin', brand: 'أتورفاستاتين', strength: '20 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 110, controlled: false },
  { code: 'DEMO2-MED-011', generic: 'Omeprazole', brand: 'أوميبرازول', strength: '20 mg', form: 'CAPSULE', route: 'ORAL', unit: 'capsule', cost: 65, controlled: false },
  { code: 'DEMO2-MED-012', generic: 'Ondansetron', brand: 'أوندانسيترون', strength: '4 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 130, controlled: false },
  { code: 'DEMO2-MED-013', generic: 'Salbutamol', brand: 'سالبوتامول', strength: '100 mcg', form: 'INHALER', route: 'INHALATION', unit: 'inhaler', cost: 1800, controlled: false },
  { code: 'DEMO2-MED-014', generic: 'Prednisolone', brand: 'بريدنيزولون', strength: '10 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 45, controlled: false },
  { code: 'DEMO2-MED-015', generic: 'Furosemide', brand: 'فوروسيميد', strength: '40 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 35, controlled: false },
  { code: 'DEMO2-MED-016', generic: 'Aspirin', brand: 'أسبرين', strength: '81 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 25, controlled: false },
  { code: 'DEMO2-MED-017', generic: 'Clopidogrel', brand: 'كلوبيدوجريل', strength: '75 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 160, controlled: false },
  { code: 'DEMO2-MED-018', generic: 'Levothyroxine', brand: 'ليفوثيروكسين', strength: '50 mcg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 40, controlled: false },
  { code: 'DEMO2-MED-019', generic: 'Metronidazole', brand: 'ميترونيدازول', strength: '500 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 50, controlled: false },
  { code: 'DEMO2-MED-020', generic: 'Ciprofloxacin', brand: 'سيبروفلوكساسين', strength: '500 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 95, controlled: false },
  { code: 'DEMO2-MED-021', generic: 'Diclofenac', brand: 'ديكلوفيناك', strength: '50 mg', form: 'TABLET', route: 'ORAL', unit: 'tablet', cost: 45, controlled: false },
  { code: 'DEMO2-MED-022', generic: 'Tramadol', brand: 'ترامادول', strength: '50 mg', form: 'CAPSULE', route: 'ORAL', unit: 'capsule', cost: 120, controlled: true }
];

const NEW_SERVICES = [
  { code: 'CONS-SPEC', name: 'استشارة طبية تخصصية', type: 'CONSULTATION', price: 7500 },
  { code: 'CONS-FUP', name: 'استشارة متابعة', type: 'CONSULTATION', price: 3000 },
  { code: 'LAB-PANEL', name: 'تحليل صورة دم كاملة', type: 'LABORATORY', price: 4500 },
  { code: 'US-ABD', name: 'أشعة تلفزيونية للبطن', type: 'RADIOLOGY', price: 9000 },
  { code: 'XR-KNEE', name: 'أشعة سينية للركبة', type: 'RADIOLOGY', price: 8000 },
  { code: 'ECG-12', name: 'تخطيط قلب كهربائي', type: 'PROCEDURE', price: 6000 },
  { code: 'PHYSIO', name: 'جلسة علاج طبيعي', type: 'PROCEDURE', price: 4000 },
  { code: 'VACC-FLU', name: 'تطعيم الإنفلونزا', type: 'PROCEDURE', price: 2500 },
  { code: 'PROC-SUTURE', name: 'خياطة جرح', type: 'PROCEDURE', price: 5500 },
  { code: 'ROOM-PRIV', name: 'إقامة غرفة خاصة - يوم', type: 'ROOM', price: 20000 },
  { code: 'ROOM-ICU', name: 'إقامة عناية مركزة - يوم', type: 'ROOM', price: 35000 },
  { code: 'NEBULIZER', name: 'جلسة تبخير', type: 'PROCEDURE', price: 3000 }
];

const NEW_DIAGNOSES = [
  { system: 'ICD-10', code: 'J06.9', description: 'التهاب الجهاز التنفسي العلوي الحاد' },
  { system: 'ICD-10', code: 'K29.7', description: 'التهاب المعدة غير المحدد' },
  { system: 'ICD-10', code: 'M54.5', description: 'ألم أسفل الظهر' },
  { system: 'ICD-10', code: 'E78.5', description: 'ارتفاع الدهون في الدم' },
  { system: 'ICD-10', code: 'G43.9', description: 'الصداع النصفي' },
  { system: 'ICD-10', code: 'L03.9', description: 'التهاب النسيج الخلوي' },
  { system: 'ICD-10', code: 'S52.5', description: 'كسر الكعبرة السفلية' },
  { system: 'ICD-10', code: 'K35.8', description: 'التهاب الزائدة الدودية الحاد' },
  { system: 'ICD-10', code: 'O80', description: 'ولادة طبيعية' },
  { system: 'ICD-10', code: 'J44.9', description: 'مرض الانسداد الرئوي المزمن' },
  { system: 'ICD-10', code: 'D50.9', description: 'فقر الدم بعوز الحديد' },
  { system: 'ICD-10', code: 'H10.9', description: 'التهاب الملتحمة' }
];

const NEW_LAB_TESTS = [
  { code: 'LOINC-2571-8', loinc: '2571-8', name: 'الدهون الثلاثية - Triglycerides', unit: 'mg/dL', range: '50-150', specimen: 'Blood', price: 3200, minutes: 90 },
  { code: 'LOINC-2093-3', loinc: '2093-3', name: 'الكوليسترول الكلي - Cholesterol', unit: 'mg/dL', range: '125-200', specimen: 'Blood', price: 3000, minutes: 90 },
  { code: 'LOINC-1975-2', loinc: '1975-2', name: 'البيليروبين - Bilirubin', unit: 'mg/dL', range: '0.1-1.2', specimen: 'Blood', price: 2800, minutes: 90 },
  { code: 'LOINC-1989-3', loinc: '1989-3', name: 'فيتامين د - Vitamin D', unit: 'ng/mL', range: '30-100', specimen: 'Blood', price: 8500, minutes: 240 },
  { code: 'LOINC-24357-6', loinc: '24357-6', name: 'تحليل بول عام - Urinalysis', unit: null, range: 'Normal', specimen: 'Urine', price: 2000, minutes: 45 },
  { code: 'LOINC-630-4', loinc: '630-4', name: 'مزرعة بول - Urine culture', unit: null, range: 'No growth', specimen: 'Urine', price: 6500, minutes: 2880 },
  { code: 'LOINC-789-8', loinc: '789-8', name: 'كريات الدم الحمراء - RBC', unit: '10^6/uL', range: '4.5-5.9', specimen: 'Blood', price: 1800, minutes: 60 },
  { code: 'LOINC-1751-7', loinc: '1751-7', name: 'الألبومين - Albumin', unit: 'g/dL', range: '3.5-5.0', specimen: 'Blood', price: 2600, minutes: 90 },
  { code: 'LOINC-2823-3B', loinc: null, name: 'تخطيط القلب - ECG', unit: null, range: 'Normal sinus rhythm', specimen: 'None', price: 6000, minutes: 30 },
  { code: 'LOINC-8339-4', loinc: '8339-4', name: 'سرعة ترسيب الدم - ESR', unit: 'mm/hr', range: '0-20', specimen: 'Blood', price: 2200, minutes: 120 }
];

const SUPPLIERS = [
  { code: 'DEMO2-SUP-001', name: 'شركة النور للأدوية والمستلزمات الطبية' },
  { code: 'DEMO2-SUP-002', name: 'مؤسسة الشفاء للتوريدات الصحية' },
  { code: 'DEMO2-SUP-003', name: 'شركة اليمن الدوائية' },
  { code: 'DEMO2-SUP-004', name: 'مستودع الأمل للمستحضرات الطبية' },
  { code: 'DEMO2-SUP-005', name: 'شركة الحياة للأجهزة والمستهلكات' },
  { code: 'DEMO2-SUP-006', name: 'مؤسسة السلام للمستلزمات المخبرية' }
];

const WARD_PLAN = [
  { code: 'ICU-B', name: 'وحدة العناية المركزة B', type: 'ICU', roomCount: 4, roomType: 'ICU', bedType: 'ICU', prefix: 'ICU' },
  { code: 'MAT-C', name: 'جناح الولادة C', type: 'MATERNITY', roomCount: 4, roomType: 'PRIVATE', bedType: 'MATERNITY', prefix: 'MAT' },
  { code: 'PED-D', name: 'جناح الأطفال D', type: 'PEDIATRIC', roomCount: 4, roomType: 'STANDARD', bedType: 'PEDIATRIC', prefix: 'PED' }
];

function seed(db, ctx) {
  const ref = ctx.ref;
  const statements = [];

  // Staff ------------------------------------------------------------------
  const staffRows = [];
  const doctorRows = [];
  const staffIds = {};
  for (const member of NEW_STAFF) {
    const id = refId();
    staffIds[member.key] = id;
    staffRows.push({
      id, branch_id: BRANCH_ID, department_id: member.dept, employee_number: member.number,
      first_name: member.first, last_name: member.last,
      work_email: member.key + '@smarthospital.demo', phone_number: '+9677' + int(10000000, 99999999),
      job_title: member.title, employment_status: 'ACTIVE',
      hired_on: sqlDate(addDays(NOW, -int(400, 2200)))
    });
    if (member.specialty) {
      doctorRows.push({
        staff_id: id, license_number: member.license, specialty: member.specialty,
        qualification: member.qualification, can_order_labs: 1, is_active: 1, branch_id: BRANCH_ID
      });
    }
  }
  statements.push(...db.insertSql('staff_members',
    ['id', 'branch_id', 'department_id', 'employee_number', 'first_name', 'last_name', 'work_email',
      'phone_number', 'job_title', 'employment_status', 'hired_on'], staffRows));
  statements.push(...db.insertSql('doctor_profiles',
    ['staff_id', 'license_number', 'specialty', 'qualification', 'can_order_labs', 'is_active', 'branch_id'], doctorRows));

  for (const member of NEW_STAFF) {
    if (!member.specialty) continue;
    ref.doctors.push({
      staffId: staffIds[member.key], firstName: member.first, lastName: member.last,
      specialty: member.specialty, branchId: BRANCH_ID, departmentId: member.dept
    });
  }
  ctx.newStaff = staffIds;

  // Wards / rooms / beds ---------------------------------------------------
  const wardRows = [];
  const roomRows = [];
  const bedRows = [];
  let bedSeq = 0;
  for (const plan of WARD_PLAN) {
    const wardId = refId();
    wardRows.push({
      id: wardId, branch_id: BRANCH_ID, department_id: DEPT.INPATIENT,
      ward_code: plan.code, ward_name: plan.name, ward_type: plan.type, is_active: 1
    });
    for (let roomNo = 1; roomNo <= plan.roomCount; roomNo += 1) {
      const roomId = refId();
      const roomNumber = plan.prefix + '-' + String(roomNo).padStart(2, '0');
      roomRows.push({
        id: roomId, branch_id: BRANCH_ID, ward_id: wardId, room_number: roomNumber,
        room_type: plan.roomType, floor_label: String(2 + (roomNo % 3)), is_active: 1
      });
      for (let bedNo = 1; bedNo <= 3; bedNo += 1) {
        bedSeq += 1;
        let status = 'AVAILABLE';
        if (bedSeq === 7 || bedSeq === 19) status = 'CLEANING';
        if (bedSeq === 26) status = 'OUT_OF_SERVICE';
        bedRows.push({
          id: refId(), branch_id: BRANCH_ID, room_id: roomId,
          bed_number: String(bedNo).padStart(2, '0'), bed_type: plan.bedType,
          operational_status: status, is_active: 1
        });
      }
    }
  }
  statements.push(...db.insertSql('wards',
    ['id', 'branch_id', 'department_id', 'ward_code', 'ward_name', 'ward_type', 'is_active'], wardRows));
  statements.push(...db.insertSql('rooms',
    ['id', 'branch_id', 'ward_id', 'room_number', 'room_type', 'floor_label', 'is_active'], roomRows));
  statements.push(...db.insertSql('beds',
    ['id', 'branch_id', 'room_id', 'bed_number', 'bed_type', 'operational_status', 'is_active'], bedRows));
  ref.beds.push(...bedRows.map((bed) => ({ id: bed.id, roomId: bed.room_id, number: bed.bed_number, type: bed.bed_type, status: bed.operational_status })));

  // Suppliers --------------------------------------------------------------
  const supplierRows = SUPPLIERS.map((supplier) => ({
    id: refId(), supplier_code: supplier.code, supplier_name: supplier.name,
    contact_person: pick(['عبدالله الشامي', 'محمد الحكيمي', 'سالم العولقي', 'أحمد السقاف', 'خالد الجابري']),
    phone_number: '+9677' + int(10000000, 99999999), email: supplier.code.toLowerCase() + '@supplier.demo',
    address_text: pick(['صنعاء - شارع الزبيري', 'عدن - المنصورة', 'تعز - شارع جمال', 'الحديدة - شارع صنعاء']),
    tax_or_registration_no: 'TAX-' + int(100000, 999999), supplier_status: 'ACTIVE'
  }));
  statements.push(...db.insertSql('suppliers',
    ['id', 'supplier_code', 'supplier_name', 'contact_person', 'phone_number', 'email', 'address_text',
      'tax_or_registration_no', 'supplier_status'], supplierRows));
  ref.suppliers.push(...supplierRows.map((row) => ({ id: row.id, code: row.supplier_code, name: row.supplier_name })));

  // Medications ------------------------------------------------------------
  const medRows = NEW_MEDICATIONS.map((med) => ({
    id: refId(), code: med.code, generic_name: med.generic, brand_name: med.brand,
    strength: med.strength, max_daily_dose: null, dose_unit: med.unit, dosage_form: med.form,
    drug_class: 'DEMO', route: med.route, dispensing_unit: med.unit,
    is_controlled: med.controlled ? 1 : 0, is_active: 1
  }));
  statements.push(...db.insertSql('medications',
    ['id', 'code', 'generic_name', 'brand_name', 'strength', 'max_daily_dose', 'dose_unit', 'dosage_form',
      'drug_class', 'route', 'dispensing_unit', 'is_controlled', 'is_active'], medRows));
  const medCosts = {};
  NEW_MEDICATIONS.forEach((med, index) => {
    medCosts[medRows[index].id] = med.cost;
    ref.medications.push({
      id: medRows[index].id, code: med.code, generic: med.generic, brand: med.brand,
      strength: med.strength, form: med.form, route: med.route, unit: med.unit, controlled: med.controlled
    });
  });
  ctx.medCosts = medCosts;

  // Services ---------------------------------------------------------------
  const serviceRows = NEW_SERVICES.map((service) => ({
    id: refId(), code: service.code, name: service.name, service_type: service.type,
    standard_price: service.price, currency_code: 'YER', is_active: 1
  }));
  statements.push(...db.insertSql('service_catalog',
    ['id', 'code', 'name', 'service_type', 'standard_price', 'currency_code', 'is_active'], serviceRows));
  ref.services.push(...serviceRows.map((row) => ({
    id: row.id, code: row.code, name: row.name, type: row.service_type, price: Number(row.standard_price)
  })));

  // Diagnoses --------------------------------------------------------------
  const diagnosisRows = NEW_DIAGNOSES.map((diagnosis) => ({
    id: refId(), code_system: diagnosis.system, diagnosis_code: diagnosis.code,
    description: diagnosis.description, is_active: 1
  }));
  statements.push(...db.insertSql('diagnosis_catalog',
    ['id', 'code_system', 'diagnosis_code', 'description', 'is_active'], diagnosisRows));
  ref.diagnoses.push(...diagnosisRows.map((row) => ({
    id: row.id, system: row.code_system, code: row.diagnosis_code, description: row.description
  })));

  // Lab tests --------------------------------------------------------------
  const labRows = NEW_LAB_TESTS.map((test) => ({
    id: refId(), code: test.code, loinc_code: test.loinc, name: test.name, code_system: 'LOINC',
    specimen_type: test.specimen, result_unit: test.unit, reference_range: test.range,
    standard_price: test.price, currency_code: 'YER', expected_minutes: test.minutes, is_active: 1
  }));
  statements.push(...db.insertSql('lab_test_catalog',
    ['id', 'code', 'loinc_code', 'name', 'code_system', 'specimen_type', 'result_unit', 'reference_range',
      'standard_price', 'currency_code', 'expected_minutes', 'is_active'], labRows));
  ref.labTests.push(...labRows.map((row) => ({
    id: row.id, code: row.code, name: row.name, unit: row.result_unit, range: row.reference_range,
    specimen: row.specimen_type, price: Number(row.standard_price), minutes: Number(row.expected_minutes)
  })));

  // Reorder policies for every demo medication -----------------------------
  const policyRows = NEW_MEDICATIONS.map(() => ({
    id: refId(), branch_id: BRANCH_ID, location_id: PHARMACY_LOCATION, medication_id: refId(),
    reorder_point: int(120, 600), minimum_quantity: int(25, 120), is_active: 1, updated_by: USER.INVENTORY,
    updated_at: NOW
  }));
  // The medication ids above must be real ids, not fresh ones.
  policyRows.forEach((row, index) => { row.medication_id = medRows[index].id; });
  ctx.reorderPolicies = policyRows;
  statements.push(...db.insertSql('inventory_reorder_policies',
    ['id', 'branch_id', 'location_id', 'medication_id', 'reorder_point', 'minimum_quantity', 'is_active',
      'updated_by', 'updated_at'], policyRows));

  db.execStatements(statements, '01-reference');
  db.log('  reference: ' + staffRows.length + ' staff, ' + wardRows.length + ' wards, ' + roomRows.length +
    ' rooms, ' + bedRows.length + ' beds, ' + medRows.length + ' medications, ' + serviceRows.length +
    ' services, ' + diagnosisRows.length + ' diagnoses, ' + labRows.length + ' lab tests');
}

module.exports = { load, seed };

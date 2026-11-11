'use strict';

/*
 * Shared helpers for the Smart Hospital demo-data generator.
 *
 * Time model
 * ----------
 * The application stores DATETIME values in UTC and renders them in the branch
 * time zone (Asia/Aden is a fixed UTC+03:00 offset with no DST). All timestamps
 * produced here are real UTC instants: helpers accept "local" wall-clock values
 * and subtract the offset before formatting.
 */

const BRANCH_ID = '10000000-0000-4000-8000-000000000001';
const CURRENCY = 'YER';
const BRANCH_OFFSET_HOURS = 3;

const DEPT = {
  GEN: '20000000-0000-4000-8000-000000000001',
  LAB: '20000000-0000-4000-8000-000000000002',
  PHARMACY: '20000000-0000-4000-8000-000000000003',
  ER: '20000000-0000-4000-8000-000000000004',
  INPATIENT: '20000000-0000-4000-8000-000000000005',
  FINANCE: '20000000-0000-4000-8000-000000000006',
  STORE: '20000000-0000-4000-8000-000000000007',
  ADMIN: '20000000-0000-4000-8000-000000000008'
};

const USER = {
  ADMIN: '40000000-0000-4000-8000-000000000001',
  RECEPTION: '40000000-0000-4000-8000-000000000002',
  DOCTOR1: '40000000-0000-4000-8000-000000000003',
  NURSE: '40000000-0000-4000-8000-000000000004',
  LABTECH: '40000000-0000-4000-8000-000000000005',
  LABSUP: '40000000-0000-4000-8000-000000000006',
  PHARMACIST: '40000000-0000-4000-8000-000000000007',
  ACCOUNTANT: '40000000-0000-4000-8000-000000000008',
  INVENTORY: '40000000-0000-4000-8000-000000000009',
  MANAGER: '40000000-0000-4000-8000-000000000010'
};

const STAFF = {
  DOCTOR1: '30000000-0000-4000-8000-000000000001',
  RECEPTION: '30000000-0000-4000-8000-000000000002',
  NURSE1: '30000000-0000-4000-8000-000000000003',
  LABTECH1: '30000000-0000-4000-8000-000000000004',
  LABSUP1: '30000000-0000-4000-8000-000000000005',
  PHARMACIST1: '30000000-0000-4000-8000-000000000006',
  ACCOUNTANT1: '30000000-0000-4000-8000-000000000007',
  INVENTORY1: '30000000-0000-4000-8000-000000000009',
  MANAGER1: '30000000-0000-4000-8000-000000000010'
};

const PHARMACY_LOCATION = '60000000-0000-4000-8000-000000000001';
const STORE_LOCATION = '60000000-0000-4000-8000-000000000002';
const FISCAL_2026 = '2f5475a2-dbb8-11f1-b8c5-508140ee520d';
const EXISTING_SUPPLIER = '95000000-0000-4000-8000-000000000001';

// Deterministic PRNG so a reseed produces the same demo hospital.
function createRng(seed) {
  let state = seed >>> 0;
  return function rng() {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const rng = createRng(20261110);

function rand() { return rng(); }
function chance(p) { return rng() < p; }
function pick(list) { return list[Math.floor(rng() * list.length)]; }
function int(min, max) { return min + Math.floor(rng() * (max - min + 1)); }
function dec(min, max, scale) { return Number((min + rng() * (max - min)).toFixed(scale === undefined ? 2 : scale)); }

function weighted(entries) {
  const total = entries.reduce((sum, entry) => sum + entry[1], 0);
  let roll = rng() * total;
  for (const entry of entries) {
    roll -= entry[1];
    if (roll <= 0) return entry[0];
  }
  return entries[entries.length - 1][0];
}

function shuffle(list) {
  const copy = list.slice();
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(rng() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

function chunk(list, size) {
  const out = [];
  for (let i = 0; i < list.length; i += size) out.push(list.slice(i, i + size));
  return out;
}

// ---------------------------------------------------------------------------
// Identifier namespaces
// ---------------------------------------------------------------------------
let txCounter = 1;
let refCounter = 1;

function txId() {
  return 'deadbeef-0000-4000-8000-' + (txCounter++).toString(16).padStart(12, '0');
}
function refId() {
  return 'feedface-0000-4000-8000-' + (refCounter++).toString(16).padStart(12, '0');
}

const TX_PREFIX = 'deadbeef-0000-4000-8000-';
const REF_PREFIX = 'feedface-0000-4000-8000-';

// ---------------------------------------------------------------------------
// Dates (UTC storage, Asia/Aden rendering)
// ---------------------------------------------------------------------------
const NOW = new Date();
const WINDOW_DAYS = 183;
const WINDOW_START = new Date(NOW.getTime() - WINDOW_DAYS * 86400000);
const FUTURE_DAYS = 30;

function utcAt(y, month, day, hour, minute) {
  return new Date(Date.UTC(y, month - 1, day, hour - BRANCH_OFFSET_HOURS, minute || 0, 0, 0));
}

function addMinutes(date, minutes) { return new Date(date.getTime() + minutes * 60000); }
function addHours(date, hours) { return addMinutes(date, hours * 60); }
function addDays(date, days) { return addMinutes(date, days * 24 * 60); }

function localDateParts(date) {
  const shifted = new Date(date.getTime() + BRANCH_OFFSET_HOURS * 3600000);
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth() + 1,
    day: shifted.getUTCDate(),
    hour: shifted.getUTCHours(),
    minute: shifted.getUTCMinutes()
  };
}

function pad(n, width) { return String(n).padStart(width || 2, '0'); }

// YYYY-MM-DD in local (branch) time.
function localDate(date) {
  const p = localDateParts(date);
  return p.year + '-' + pad(p.month) + '-' + pad(p.day);
}

// Local date string minus N days.
function localDateOffset(date, days) {
  return localDate(addDays(date, -days));
}

function sqlDateTime(date) {
  const iso = date.toISOString();
  return iso.slice(0, 10) + ' ' + iso.slice(11, 19) + '.' + iso.slice(20, 23);
}

function sqlDate(date) {
  return date.toISOString().slice(0, 10);
}

function dayStartLocal(date) {
  const p = localDateParts(date);
  return utcAt(p.year, p.month, p.day, 0, 0);
}

// A random instant within local working hours for the given day.
function workingInstant(day, startHour, endHour) {
  const p = localDateParts(day);
  const hour = int(startHour, endHour - 1);
  const minute = pick([0, 10, 15, 20, 30, 30, 40, 45, 50]);
  return utcAt(p.year, p.month, p.day, hour, minute);
}

function eachDay(from, to) {
  const days = [];
  let cursor = dayStartLocal(from);
  const limit = dayStartLocal(to).getTime();
  while (cursor.getTime() <= limit) {
    days.push(cursor);
    cursor = addDays(cursor, 1);
  }
  return days;
}

// ---------------------------------------------------------------------------
// SQL literal encoding
// ---------------------------------------------------------------------------
function sqlLiteral(value) {
  if (value === null || value === undefined) return 'NULL';
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) throw new Error('Non-finite number in SQL literal');
    return String(value);
  }
  if (typeof value === 'boolean') return value ? '1' : '0';
  if (value instanceof Date) return "'" + sqlDateTime(value) + "'";
  const text = String(value);
  return "'" + text.replace(/\\/g, '\\\\').replace(/'/g, "''").replace(/\u0000/g, '') + "'";
}

function sqlJson(value) {
  return sqlLiteral(JSON.stringify(value));
}

// ---------------------------------------------------------------------------
// Arabic demo content
// ---------------------------------------------------------------------------
const MALE_NAMES = [
  'محمد', 'أحمد', 'علي', 'عبدالله', 'خالد', 'يوسف', 'سالم', 'صالح', 'فهد', 'عمر',
  'إبراهيم', 'حسن', 'حسين', 'طارق', 'زياد', 'سامي', 'نبيل', 'وليد', 'ماجد', 'راشد',
  'عبدالرحمن', 'عبدالعزيز', 'ياسر', 'بدر', 'أنور', 'عمار', 'هشام', 'كريم', 'منصور', 'جمال',
  'سعيد', 'ناصر', 'فيصل', 'عدنان', 'علاء', 'مروان', 'شادي', 'بشار', 'أيمن', 'حاتم',
  'عصام', 'توفيق', 'منير', 'رائد', 'عبدالكريم', 'قاسم', 'مثنى', 'حمزة', 'بلال', 'أسامة'
];

const FEMALE_NAMES = [
  'فاطمة', 'عائشة', 'مريم', 'خديجة', 'زينب', 'سارة', 'نورة', 'هند', 'أمل', 'ليلى',
  'سميرة', 'نجاة', 'إيمان', 'سعاد', 'حنان', 'رحمة', 'أسماء', 'هيام', 'رنا', 'لمياء',
  'أروى', 'بشرى', 'تهاني', 'جميلة', 'حياة', 'خلود', 'دلال', 'رشا', 'زهرة', 'سلوى',
  'شيماء', 'صفاء', 'علا', 'غادة', 'فردوس', 'كوثر', 'لبنى', 'ماجدة', 'نادية', 'نسرين',
  'نوال', 'هدى', 'وفاء', 'يسرى', 'آمنة', 'بدرية', 'ثريا', 'حورية', 'سمية', 'شادية'
];

const FAMILY_NAMES = [
  'المعمري', 'الحاشدي', 'العولقي', 'البكري', 'الحداد', 'السقاف', 'الشامي', 'العمري', 'اليزيدي', 'المقطري',
  'الشرعبي', 'القرشي', 'المروني', 'الجابري', 'الرويشاني', 'العزي', 'الصلوي', 'الشميري', 'الحكيمي', 'الذماري',
  'الرداعي', 'الأهدل', 'البركاني', 'الحسني', 'الطيري', 'العريقي', 'القباطي', 'المخلافي', 'الهتاري', 'الوصابي',
  'باعوم', 'بامطرف', 'بن حبتور', 'باوزير', 'العطاس', 'الجنيد', 'الأنصاري', 'المهري', 'الكثيري', 'اليافعي'
];

const CITIES = ['صنعاء', 'عدن', 'تعز', 'الحديدة', 'إب', 'المكلا', 'سيئون', 'ذمار', 'حجة', 'عمران', 'البيضاء', 'شبام'];
const STREETS = ['شارع الزبيري', 'شارع حدة', 'شارع تعز', 'شارع الستين', 'شارع القصر', 'شارع المطار', 'شارع جمال', 'شارع 26 سبتمبر', 'شارع الحرية'];

const ALLERGENS = ['البنسلين', 'السلفا', 'الأسبرين', 'الفول السوداني', 'اللاتكس', 'المورفين', 'المضادات الحيوية', 'حبوب اللقاح', 'الغلوتين', 'المكسرات'];
const REACTIONS = ['طفح جلدي', 'تورم موضعي', 'صعوبة في التنفس', 'حكة شديدة', 'اضطراب معوي', 'انخفاض ضغط الدم', 'تدميع العينين'];

const COMPLAINTS = [
  'صداع مستمر منذ ثلاثة أيام', 'ألم في الصدر عند المجهود', 'حمى وسعال جاف', 'ألم أسفل البطن',
  'غثيان وقيء متكرر', 'ألم أسفل الظهر', 'ضيق في التنفس', 'دوخة وتعب عام', 'ألم في المفاصل',
  'التهاب الحلق وصعوبة البلع', 'ألم في الأذن اليمنى', 'حكة جلدية منتشرة', 'تورم في الساقين',
  'خفقان في القلب', 'ألم أثناء التبول', 'إسهال مائي منذ يومين', 'نزيف من الأنف', 'ألم في العين اليسرى',
  'ارتفاع في سكر الدم', 'متابعة نتائج التحاليل', 'تجديد وصفة دواء مزمن', 'ألم في الركبة اليمنى'
];

const APPOINTMENT_REASONS = [
  'استشارة عامة', 'متابعة مرض مزمن', 'فحص دوري', 'تجديد وصفة طبية', 'قراءة نتائج تحاليل',
  'ألم حاد', 'إصابة طفيفة', 'استشارة تخصصية', 'متابعة بعد عملية', 'تطعيم'
];

const DISCHARGE_SUMMARIES = [
  'تحسنت الحالة العامة للمريض وتم ضبط الأعراض دوائياً مع توصية بالمتابعة في العيادة الخارجية.',
  'أُجريت الفحوصات اللازمة وثبت استقرار العلامات الحيوية، وتم تسريح المريض مع خطة علاج منزلية.',
  'تمت السيطرة على الالتهاب بالمضادات الحيوية الوريدية وتحول المريض إلى العلاج الفموي قبل التسريح.',
  'استقرت الحالة بعد تعديل جرعات الأدوية المزمنة، مع تعليمات غذائية ومتابعة أسبوعية.',
  'تم علاجه من الجفاف ونقص السوائل ومراقبة وظائف الكلى حتى الاستقرار التام.'
];

module.exports = {
  BRANCH_ID, CURRENCY, BRANCH_OFFSET_HOURS, DEPT, USER, STAFF,
  PHARMACY_LOCATION, STORE_LOCATION, FISCAL_2026, EXISTING_SUPPLIER,
  TX_PREFIX, REF_PREFIX,
  NOW, WINDOW_START, WINDOW_DAYS, FUTURE_DAYS,
  rand, chance, pick, int, dec, weighted, shuffle, chunk,
  txId, refId,
  utcAt, addMinutes, addHours, addDays, localDateParts, localDate, localDateOffset,
  sqlDateTime, sqlDate, dayStartLocal, workingInstant, eachDay,
  sqlLiteral, sqlJson,
  MALE_NAMES, FEMALE_NAMES, FAMILY_NAMES, CITIES, STREETS,
  ALLERGENS, REACTIONS, COMPLAINTS, APPOINTMENT_REASONS, DISCHARGE_SUMMARIES
};

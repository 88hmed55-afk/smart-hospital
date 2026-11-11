import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Users } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { PageResponse, PatientRecord, PatientSummary } from '../types/api';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';
import { useOutletContext } from 'react-router-dom';

const sexLabel: Record<string, string> = { MALE: 'ذكر', FEMALE: 'أنثى', UNKNOWN: 'غير معروف', NOT_RECORDED: 'غير مسجل' };
const severityLabel: Record<string, string> = { MILD: 'خفيفة', MODERATE: 'متوسطة', SEVERE: 'شديدة', UNKNOWN: 'غير محددة' };

const patientExportColumns: ExportColumn<PatientSummary>[] = [
  { header: 'رقم الملف', value: (row) => row.mrn, width: 1.1 },
  { header: 'اسم المريض', value: (row) => [row.firstName, row.middleName, row.lastName].filter(Boolean).join(' '), width: 2.2 },
  { header: 'الجنس', value: (row) => sexLabel[row.sex] ?? row.sex, width: 0.8 },
  { header: 'تاريخ الميلاد', value: (row) => row.dateOfBirth || '—', width: 1, align: 'center' },
  { header: 'الهاتف', value: (row) => row.primaryPhone || '—', width: 1.1, align: 'left' },
  { header: 'الحالة', value: (row) => (row.status === 'ACTIVE' ? 'نشط' : row.status), width: 0.8, align: 'center' },
];
const now = new Date();
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
function makeMrn() { return `SH-${today.replaceAll('-', '')}-${Math.random().toString(36).slice(2, 7).toUpperCase()}`; }

export default function PatientsPage() {
  const { token, user } = useAuth();
  const { branchId, branch } = useOutletContext<LayoutOutletContext>();
  const queryClient = useQueryClient();
  const [term, setTerm] = useState('');
  const [submittedTerm, setSubmittedTerm] = useState('');
  const [page, setPage] = useState(0);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(null);
  const canRegister = user?.roleCodes.some((role) => ['HOSPITAL_ADMIN', 'RECEPTIONIST'].includes(role)) ?? false;

  const patientsQuery = useQuery({
    queryKey: ['patients', submittedTerm, page],
    queryFn: () => apiRequest<PageResponse<PatientSummary>>(`/patients?offset=${page * 20}&limit=20${submittedTerm ? `&q=${encodeURIComponent(submittedTerm)}` : ''}`, token!),
    enabled: Boolean(token),
  });
  const detailQuery = useQuery({
    queryKey: ['patient-detail', selectedPatientId],
    queryFn: () => apiRequest<PatientRecord>(`/patients/${selectedPatientId}`, token!),
    enabled: Boolean(token && selectedPatientId),
  });
  const registerMutation = useMutation({
    mutationFn: (body: PatientRegistrationBody) => apiRequest<PatientRecord>('/patients', token!, { method: 'POST', body: JSON.stringify({ ...body, branchId }) }),
    onSuccess: async (patient) => {
      setRegisterOpen(false);
      setSelectedPatientId(patient.id);
      setTerm(''); setSubmittedTerm(''); setPage(0);
      await queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
  });

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmittedTerm(term.trim()); setPage(0);
  }

  return (
    <div className="module-page">
      <PageHeader
        eyebrow="PATIENT MANAGEMENT"
        title="إدارة المرضى"
        description="سجل موحد للمرضى مع احترام نطاق الوصول الممنوح لكل فرع، وبحث فوري برقم الملف أو الاسم."
        icon={Users}
        meta={[patientsQuery.data ? `${patientsQuery.data.total.toLocaleString('ar-YE')} سجل` : 'سجلات المرضى']}
        actions={
          <>
            <ExportMenu
              fileName="سجل-المرضى"
              title="تقرير سجل المرضى"
              subtitle="قائمة المرضى المطابقة لعبارة البحث الحالية"
              meta={[
                { label: 'الفرع', value: branch?.name ?? 'الفرع الحالي' },
                { label: 'عبارة البحث', value: submittedTerm || 'كل السجلات' },
              ]}
              columns={patientExportColumns}
              rows={patientsQuery.data?.items ?? []}
            />
            {canRegister && (
              <button className="primary-button" onClick={() => setRegisterOpen(true)}>
                <Plus size={16} /> تسجيل مريض جديد
              </button>
            )}
          </>
        }
      />
      <section className="panel patient-list-panel">
        <div className="patient-toolbar">
          <form className="patient-search" onSubmit={submitSearch}>
            <span>⌕</span><input value={term} onChange={(event) => setTerm(event.target.value)} placeholder="ابحث برقم الملف أو اسم المريض" aria-label="البحث عن مريض" />
            <button className="secondary-button">بحث</button>
          </form>
          <span className="result-count">{patientsQuery.data ? `${patientsQuery.data.total.toLocaleString('ar-YE')} سجل` : 'سجلات المرضى'}</span>
        </div>
        {patientsQuery.isError && <div className="alert alert-error">{patientsQuery.error instanceof ApiError ? patientsQuery.error.message : 'تعذر تحميل قائمة المرضى.'}</div>}
        {patientsQuery.isLoading ? <div className="empty-state">جارٍ تحميل السجلات…</div> : patientsQuery.data?.items.length ? (
          <div className="table-wrap"><table className="data-table"><thead><tr><th>رقم الملف</th><th>اسم المريض</th><th>الجنس</th><th>تاريخ الميلاد</th><th>الهاتف</th><th>الحالة</th><th /></tr></thead>
            <tbody>{patientsQuery.data.items.map((patient) => <tr key={patient.id}>
              <td><strong className="mrn-text">{patient.mrn}</strong></td>
              <td><div className="patient-name-cell"><span className="patient-row-avatar">{patient.firstName.slice(0, 1)}</span><span>{patient.firstName} {patient.middleName ? `${patient.middleName} ` : ''}{patient.lastName}</span></div></td>
              <td>{sexLabel[patient.sex] ?? patient.sex}</td>
              <td>{patient.dateOfBirth || '—'}</td>
              <td dir="ltr" className="ltr-cell">{patient.primaryPhone || '—'}</td>
              <td><span className={`patient-status ${patient.status === 'ACTIVE' ? 'status-active' : 'status-inactive'}`}>{patient.status === 'ACTIVE' ? 'نشط' : patient.status}</span></td>
              <td><button className="table-action" onClick={() => setSelectedPatientId(patient.id)}>عرض الملف ←</button></td>
            </tr>)}</tbody></table></div>
        ) : <div className="empty-state"><span>♙</span><strong>لا توجد سجلات مطابقة</strong><small>جرّب تغيير عبارة البحث أو سجّل مريضاً جديداً.</small></div>}
        {patientsQuery.data && patientsQuery.data.total > 20 && <div className="pagination"><button disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>السابق</button><span>صفحة {page + 1} من {Math.ceil(patientsQuery.data.total / 20)}</span><button disabled={(page + 1) * 20 >= patientsQuery.data.total} onClick={() => setPage((current) => current + 1)}>التالي</button></div>}
      </section>
      {selectedPatientId && <PatientDetail patient={detailQuery.data} loading={detailQuery.isLoading} error={detailQuery.error} onClose={() => setSelectedPatientId(null)} />}
      {registerOpen && <PatientRegistrationModal busy={registerMutation.isPending} error={registerMutation.error} onClose={() => { setRegisterOpen(false); registerMutation.reset(); }} onSubmit={(body) => registerMutation.mutate(body)} />}
    </div>
  );
}

type PatientRegistrationBody = {
  mrn: string; firstName: string; middleName?: string; lastName: string; dateOfBirth?: string;
  sex: 'MALE' | 'FEMALE' | 'UNKNOWN'; bloodGroup?: string; primaryPhone?: string; email?: string;
  addressText?: string; preferredLanguage: string; contacts: never[];
  allergies: { allergenName: string; reaction?: string; severity: string; verificationStatus: string }[];
};

function PatientRegistrationModal({ busy, error, onClose, onSubmit }: {
  busy: boolean; error: unknown; onClose: () => void; onSubmit: (body: PatientRegistrationBody) => void;
}) {
  const [form, setForm] = useState({ mrn: makeMrn(), firstName: '', middleName: '', lastName: '', dateOfBirth: '', sex: 'UNKNOWN' as PatientRegistrationBody['sex'], bloodGroup: '', primaryPhone: '', email: '', addressText: '', allergen: '', reaction: '', severity: 'UNKNOWN' });
  function update(field: keyof typeof form, value: string) { setForm((old) => ({ ...old, [field]: value })); }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({
      mrn: form.mrn, firstName: form.firstName, middleName: form.middleName || undefined, lastName: form.lastName,
      dateOfBirth: form.dateOfBirth || undefined, sex: form.sex, bloodGroup: form.bloodGroup || undefined,
      primaryPhone: form.primaryPhone || undefined, email: form.email || undefined, addressText: form.addressText || undefined,
      preferredLanguage: 'ar', contacts: [],
      allergies: form.allergen.trim() ? [{ allergenName: form.allergen.trim(), reaction: form.reaction || undefined, severity: form.severity, verificationStatus: 'UNVERIFIED' }] : [],
    });
  }
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="modal-card patient-register-modal" role="dialog" aria-modal="true" aria-labelledby="register-title">
      <div className="modal-heading"><div><span className="eyebrow">NEW PATIENT</span><h2 id="register-title">تسجيل مريض جديد</h2><p>تُمنح صلاحية ملف المريض تلقائياً للفرع الافتراضي لحسابك.</p></div><button className="modal-close" onClick={onClose} aria-label="إغلاق">×</button></div>
      <form onSubmit={submit} className="modal-form">
        <div className="form-grid two-cols"><label>رقم الملف الطبي<input required maxLength={32} value={form.mrn} onChange={(e) => update('mrn', e.target.value)} /><small>تم اقتراح رقم فريد؛ تحقق منه قبل الحفظ.</small></label><label>تاريخ الميلاد<input type="date" max={today} value={form.dateOfBirth} onChange={(e) => update('dateOfBirth', e.target.value)} /></label></div>
        <div className="form-grid three-cols"><label>الاسم الأول<input required maxLength={80} value={form.firstName} onChange={(e) => update('firstName', e.target.value)} /></label><label>الاسم الأوسط<input maxLength={80} value={form.middleName} onChange={(e) => update('middleName', e.target.value)} /></label><label>اسم العائلة<input required maxLength={80} value={form.lastName} onChange={(e) => update('lastName', e.target.value)} /></label></div>
        <div className="form-grid three-cols"><label>الجنس<select value={form.sex} onChange={(e) => update('sex', e.target.value)}><option value="UNKNOWN">غير معروف</option><option value="MALE">ذكر</option><option value="FEMALE">أنثى</option></select></label><label>فصيلة الدم<select value={form.bloodGroup} onChange={(e) => update('bloodGroup', e.target.value)}><option value="">غير معروفة</option>{['A+','A-','B+','B-','AB+','AB-','O+','O-'].map((v) => <option key={v}>{v}</option>)}</select></label><label>رقم الهاتف<input dir="ltr" maxLength={32} value={form.primaryPhone} onChange={(e) => update('primaryPhone', e.target.value)} /></label></div>
        <div className="form-grid two-cols"><label>البريد الإلكتروني<input type="email" maxLength={254} value={form.email} onChange={(e) => update('email', e.target.value)} /></label><label>العنوان<input maxLength={500} value={form.addressText} onChange={(e) => update('addressText', e.target.value)} /></label></div>
        <div className="allergy-entry"><div className="allergy-heading"><span>＋</span><strong>إضافة حساسية معروفة (اختياري)</strong></div><div className="form-grid three-cols"><label>مسبب الحساسية<input maxLength={160} value={form.allergen} onChange={(e) => update('allergen', e.target.value)} placeholder="اسم المادة" /></label><label>رد الفعل<input maxLength={500} value={form.reaction} onChange={(e) => update('reaction', e.target.value)} /></label><label>الشدة<select value={form.severity} onChange={(e) => update('severity', e.target.value)}><option value="UNKNOWN">غير محددة</option><option value="MILD">خفيفة</option><option value="MODERATE">متوسطة</option><option value="SEVERE">شديدة</option></select></label></div><small>ستُسجل الحساسية بحالة «غير متحقق منها» إلى أن يراجعها مختص مخول.</small></div>
        {error ? <div className="alert alert-error">{error instanceof Error ? error.message : 'تعذر تسجيل المريض.'}</div> : null}
        <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>إلغاء</button><button className="primary-button" disabled={busy}>{busy ? 'جارٍ الحفظ…' : 'حفظ ملف المريض'}</button></div>
      </form>
    </section>
  </div>;
}

function PatientDetail({ patient, loading, error, onClose }: { patient?: PatientRecord; loading: boolean; error: unknown; onClose: () => void }) {
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="modal-card patient-detail-modal" role="dialog" aria-modal="true" aria-labelledby="patient-detail-title">
      <div className="modal-heading"><div><span className="eyebrow">ELECTRONIC PATIENT RECORD</span><h2 id="patient-detail-title">الملف الطبي الأساسي</h2></div><button className="modal-close" onClick={onClose} aria-label="إغلاق">×</button></div>
      {loading ? <div className="empty-state">جارٍ تحميل الملف…</div> : error ? <div className="alert alert-error">{error instanceof Error ? error.message : 'تعذر فتح الملف.'}</div> : patient && <>
        <div className="patient-profile-head"><div className="patient-profile-avatar">{patient.firstName.slice(0, 1)}</div><div><h3>{patient.firstName} {patient.middleName || ''} {patient.lastName}</h3><span>{patient.mrn}</span></div><span className="patient-status status-active">{patient.status === 'ACTIVE' ? 'نشط' : patient.status}</span></div>
        <div className="detail-grid"><div><small>تاريخ الميلاد</small><strong>{patient.dateOfBirth || 'غير مسجل'}</strong></div><div><small>الجنس</small><strong>{sexLabel[patient.sex] ?? patient.sex}</strong></div><div><small>فصيلة الدم</small><strong>{patient.bloodGroup || 'غير معروفة'}</strong></div><div><small>الهاتف</small><strong dir="ltr">{patient.primaryPhone || 'غير مسجل'}</strong></div><div><small>البريد الإلكتروني</small><strong>{patient.email || 'غير مسجل'}</strong></div><div><small>العنوان</small><strong>{patient.addressText || 'غير مسجل'}</strong></div></div>
        <div className="detail-section"><h4>الحساسية المسجلة</h4>{patient.allergies?.length ? patient.allergies.map((allergy) => <div className="allergy-row" key={allergy.id}><strong>{allergy.allergenName}</strong><span>{allergy.reaction || 'لا توجد تفاصيل لرد الفعل'}</span><span className="allergy-severity">{severityLabel[allergy.severity] ?? allergy.severity}</span><small>{allergy.verificationStatus === 'UNVERIFIED' ? 'غير متحقق منها' : allergy.verificationStatus}</small></div>) : <p className="muted-text">لا توجد حساسيات مسجلة في هذا الملف.</p>}</div>
        <div className="detail-section"><h4>جهات الاتصال</h4>{patient.contacts?.length ? patient.contacts.map((contact) => <div className="contact-row" key={contact.id}><strong>{contact.name || contact.type}</strong><span>{contact.phoneNumber || contact.email || '—'}</span><small>{contact.primary ? 'جهة الاتصال الأساسية' : contact.relationship || contact.type}</small></div>) : <p className="muted-text">لا توجد جهات اتصال إضافية.</p>}</div>
        <div className="record-caution">هذا العرض هو ملخص ديموغرافي وحساسيات مسجلة فقط، وليس بديلاً عن مراجعة السجل السريري المعتمد.</div>
      </>}
    </section>
  </div>;
}

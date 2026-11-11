import { useState, type FormEvent, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { Stethoscope } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { Department, PageResponse, PatientSummary } from '../types/api';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

interface Encounter {
  id: string; branchId: string; patientId: string; departmentId: string;
  encounterType: string; status: string; chiefComplaint?: string | null; startedAt: string;
}
interface VitalSign {
  id: string; measuredAt: string; temperatureC?: number | null; systolicMmhg?: number | null;
  diastolicMmhg?: number | null; pulseBpm?: number | null; respiratoryRate?: number | null;
  oxygenSaturation?: number | null; weightKg?: number | null; heightCm?: number | null; notes?: string | null;
}
interface ClinicalNote { id: string; noteType: string; noteText: string; noteStatus: string; createdAt: string; signedAt?: string | null; }
interface Diagnosis { id: string; diagnosisCode: string; description: string; clinicalStatus: string; primary: boolean; }
interface DiagnosisOption { id: string; diagnosisCode: string; description: string; codeSystem: string; }
interface Medication { id: string; code: string; genericName: string; brandName?: string; strength?: string; dosageForm?: string; dispensingUnit: string; controlled: boolean; }
interface LabTest { id: string; code: string; name: string; specimenType: string; standardPrice: number; currencyCode: string; }
interface BedOption { id:string; wardName:string; roomNumber:string; bedNumber:string; bedType:string; available:boolean; }
interface Prescription { id: string; status: string; orderedAt: string; items: { id: string; medicationName: string; strength?: string; dose: string; route?: string; frequency: string; durationDays?: number; quantityPrescribed: number; quantityDispensed: number; quantityRemaining: number; instructions?: string; }[]; }

const noteTypes = ['HISTORY', 'EXAMINATION', 'ASSESSMENT', 'PLAN', 'PROGRESS', 'DISCHARGE', 'OTHER'];
const noteLabels: Record<string, string> = { HISTORY: 'التاريخ المرضي', EXAMINATION: 'الفحص السريري', ASSESSMENT: 'التقييم', PLAN: 'الخطة العلاجية', PROGRESS: 'ملاحظات المتابعة', DISCHARGE: 'الخروج', OTHER: 'أخرى' };
const errorMessage = (error: unknown) => error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'تعذر تنفيذ العملية.';
const dateLabel = (value: string) => new Intl.DateTimeFormat('ar-YE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));

export default function ClinicalPage() {
  const { token, user } = useAuth();
  const { branchId } = useOutletContext<LayoutOutletContext>();
  const qc = useQueryClient();
  const isDoctor = user?.roleCodes.includes('DOCTOR') ?? false;
  const [patientQuery, setPatientQuery] = useState('');
  const [patientSearch, setPatientSearch] = useState('');
  const [patientId, setPatientId] = useState('');
  const [encounterId, setEncounterId] = useState('');
  const [openEncounter, setOpenEncounter] = useState(false);
  const [departmentId, setDepartmentId] = useState('');
  const [encounterType, setEncounterType] = useState('OUTPATIENT');
  const [chiefComplaint, setChiefComplaint] = useState('');
  const [showVitals, setShowVitals] = useState(false);
  const [showNote, setShowNote] = useState(false);
  const [showDiagnosis, setShowDiagnosis] = useState(false);
  const [showPrescription, setShowPrescription] = useState(false);
  const [showLabOrder, setShowLabOrder] = useState(false);
  const [showAdmission, setShowAdmission] = useState(false);
  const [notice, setNotice] = useState('');

  const patientsQuery = useQuery({
    queryKey: ['clinical-patient-search', patientSearch],
    queryFn: () => apiRequest<PageResponse<PatientSummary>>(`/patients?q=${encodeURIComponent(patientSearch)}&offset=0&limit=20`, token!),
    enabled: Boolean(token && patientSearch.length >= 2),
  });
  const encountersQuery = useQuery({
    queryKey: ['patient-encounters', patientId],
    queryFn: () => apiRequest<Encounter[]>(`/patients/${patientId}/encounters?limit=30`, token!),
    enabled: Boolean(token && patientId),
  });
  const departmentsQuery = useQuery({
    queryKey: ['clinical-departments', branchId],
    queryFn: () => apiRequest<Department[]>(`/organization/branches/${branchId}/departments`, token!),
    enabled: Boolean(token && branchId && isDoctor),
  });
  const encounterQuery = useQuery({ queryKey: ['encounter', encounterId], queryFn: () => apiRequest<Encounter>(`/encounters/${encounterId}`, token!), enabled: Boolean(token && encounterId) });
  const vitalsQuery = useQuery({ queryKey: ['encounter-vitals', encounterId], queryFn: () => apiRequest<VitalSign[]>(`/encounters/${encounterId}/vitals`, token!), enabled: Boolean(token && encounterId) });
  const notesQuery = useQuery({ queryKey: ['encounter-notes', encounterId], queryFn: () => apiRequest<ClinicalNote[]>(`/encounters/${encounterId}/notes`, token!), enabled: Boolean(token && encounterId) });
  const diagnosesQuery = useQuery({ queryKey: ['encounter-diagnoses', encounterId], queryFn: () => apiRequest<Diagnosis[]>(`/encounters/${encounterId}/diagnoses`, token!), enabled: Boolean(token && encounterId) });
  const prescriptionsQuery = useQuery({ queryKey: ['encounter-prescriptions', encounterId], queryFn: () => apiRequest<Prescription[]>(`/encounters/${encounterId}/prescriptions`, token!), enabled: Boolean(token && encounterId && isDoctor) });

  const openMutation = useMutation({
    mutationFn: () => apiRequest<Encounter>('/encounters', token!, { method: 'POST', body: JSON.stringify({ branchId, patientId, departmentId, encounterType, chiefComplaint: chiefComplaint.trim() || undefined }) }),
    onSuccess: async (created) => { setEncounterId(created.id); setOpenEncounter(false); setNotice('تم فتح الزيارة الطبية.'); await qc.invalidateQueries({ queryKey: ['patient-encounters', patientId] }); },
  });
  const completeMutation = useMutation({ mutationFn: () => apiRequest<Encounter>(`/encounters/${encounterId}/complete`, token!, { method: 'POST' }), onSuccess: async () => { setNotice('تم إغلاق الزيارة.'); await qc.invalidateQueries({ queryKey: ['encounter', encounterId] }); await qc.invalidateQueries({ queryKey: ['patient-encounters', patientId] }); } });

  function searchPatients(event: FormEvent) { event.preventDefault(); setPatientSearch(patientQuery.trim()); }
  const selectedPatient = patientsQuery.data?.items.find((p) => p.id === patientId);
  const activeEncounter = encounterQuery.data;
  const encounterIsOpen = activeEncounter?.status === 'OPEN' || activeEncounter?.status === 'IN_PROGRESS';

  type ClinicalExportRow = { section: string; item: string; detail: string; at: string };
  const clinicalExportColumns: ExportColumn<ClinicalExportRow>[] = [
    { header: 'القسم', value: (row) => row.section, width: 1 },
    { header: 'العنصر', value: (row) => row.item, width: 1.6 },
    { header: 'التفاصيل', value: (row) => row.detail, width: 3 },
    { header: 'التاريخ', value: (row) => row.at, width: 1.3 },
  ];
  const clinicalExportRows: ClinicalExportRow[] = [
    ...(vitalsQuery.data ?? []).map((vital) => ({
      section: 'علامات حيوية',
      item: `حرارة ${vital.temperatureC ?? '—'} · ضغط ${vital.systolicMmhg ?? '—'}/${vital.diastolicMmhg ?? '—'} · نبض ${vital.pulseBpm ?? '—'}`,
      detail: `أكسجين ${vital.oxygenSaturation ?? '—'}% · تنفس ${vital.respiratoryRate ?? '—'} · وزن ${vital.weightKg ?? '—'} · طول ${vital.heightCm ?? '—'}`,
      at: dateLabel(vital.measuredAt),
    })),
    ...(notesQuery.data ?? []).map((note) => ({
      section: 'ملاحظة سريرية',
      item: `${noteLabels[note.noteType] ?? note.noteType} · ${note.noteStatus === 'SIGNED' ? 'معتمدة' : 'مسودة'}`,
      detail: note.noteText,
      at: dateLabel(note.createdAt),
    })),
    ...(diagnosesQuery.data ?? []).map((diagnosis) => ({
      section: 'تشخيص',
      item: `${diagnosis.diagnosisCode} · ${diagnosis.description}`,
      detail: `${diagnosis.primary ? 'رئيسي · ' : ''}${diagnosis.clinicalStatus}`,
      at: dateLabel(activeEncounter?.startedAt ?? new Date().toISOString()),
    })),
    ...(prescriptionsQuery.data ?? []).flatMap((prescription) => prescription.items.map((item) => ({
      section: 'وصفة دوائية',
      item: `${item.medicationName} ${item.strength ?? ''}`.trim(),
      detail: `${item.dose} · ${item.route ?? '—'} · ${item.frequency} · مطلوب ${item.quantityPrescribed} · متبقٍ ${item.quantityRemaining}`,
      at: dateLabel(prescription.orderedAt),
    }))),
  ];

  return <div className="module-page">
    <PageHeader
      eyebrow="CLINICAL WORKSPACE"
      title="الزيارات والسجل السريري"
      description="توثيق الزيارة الطبية والملاحظات والعلامات الحيوية والتشخيصات والوصفات في مسار واحد."
      icon={Stethoscope}
      meta={[selectedPatient ? `${selectedPatient.firstName} ${selectedPatient.lastName}` : 'لم يُحدد مريض', `${encountersQuery.data?.length ?? 0} زيارة`]}
      actions={<ExportMenu fileName="ملخص-الزيارة" title="تقرير ملخص الزيارة السريرية" subtitle={selectedPatient ? `${selectedPatient.firstName} ${selectedPatient.lastName} · ${selectedPatient.mrn}` : 'الزيارة المحددة'} meta={[{ label: 'الحالة', value: activeEncounter?.status ?? 'غير محددة' }]} columns={clinicalExportColumns} rows={clinicalExportRows} disabled={!encounterId} />}
    />
    {notice && <div className="alert alert-success">{notice}</div>}
    <section className="panel clinical-search-panel">
      <div className="panel-heading"><div><h2>اختيار المريض</h2><p>ابحث برقم الملف الطبي أو الاسم أو رقم التواصل.</p></div></div>
      <form className="inline-search" onSubmit={searchPatients}><input value={patientQuery} onChange={(e) => setPatientQuery(e.target.value)} placeholder="رقم الملف أو اسم المريض…" minLength={2}/><button className="primary-button" type="submit" disabled={patientQuery.trim().length < 2}>بحث</button></form>
      {patientsQuery.isError && <div className="alert alert-error">{errorMessage(patientsQuery.error)}</div>}
      {patientsQuery.data && <div className="patient-pick-list">{patientsQuery.data.items.map((p) => <button type="button" key={p.id} className={`patient-pick ${patientId === p.id ? 'patient-pick-active' : ''}`} onClick={() => { setPatientId(p.id); setEncounterId(''); setNotice(''); }}><span><strong>{[p.firstName,p.middleName,p.lastName].filter(Boolean).join(' ')}</strong><small>{p.mrn} · {p.primaryPhone || 'لا يوجد هاتف'}</small></span><span>اختيار ←</span></button>)}{!patientsQuery.data.items.length && <div className="empty-state">لا توجد نتائج مطابقة.</div>}</div>}
    </section>
    {patientId && <section className="panel clinical-encounters-panel">
      <div className="panel-heading"><div><h2>الزيارات الطبية</h2><p>{selectedPatient ? `${selectedPatient.firstName} ${selectedPatient.lastName} · ${selectedPatient.mrn}` : 'المريض المحدد'}</p></div>{isDoctor && <button className="primary-button" onClick={() => setOpenEncounter(true)}>＋ فتح زيارة</button>}</div>
      {encountersQuery.isError && <div className="alert alert-error">{errorMessage(encountersQuery.error)}</div>}
      {encountersQuery.isLoading ? <div className="empty-state">جارٍ تحميل الزيارات…</div> : encountersQuery.data?.length ? <div className="encounter-list">{encountersQuery.data.map((e) => <button key={e.id} className={`encounter-row ${encounterId === e.id ? 'encounter-row-active' : ''}`} onClick={() => { setEncounterId(e.id); setNotice(''); }}><span className="encounter-mark">✚</span><span className="encounter-main"><strong>{e.encounterType} · {e.status}</strong><small>{dateLabel(e.startedAt)} · {e.chiefComplaint || 'دون شكوى مسجلة'}</small></span><span>عرض التفاصيل ←</span></button>)}</div> : <div className="empty-state"><strong>لا توجد زيارات ظاهرة لهذا المريض</strong><small>{isDoctor ? 'يمكنك فتح زيارة جديدة إذا كان المريض ضمن نطاق صلاحيتك.' : 'ستظهر هنا الزيارات التي تم تكليفك بها ضمن فريق الرعاية.'}</small></div>}
    </section>}
    {encounterId && <section className="panel encounter-detail-panel">
      <div className="panel-heading"><div><h2>تفاصيل الزيارة</h2><p>{activeEncounter ? `الحالة: ${activeEncounter.status} · ${activeEncounter.chiefComplaint || 'لا توجد شكوى رئيسية'}` : 'جارٍ تحميل تفاصيل الزيارة…'}</p></div>{isDoctor && encounterIsOpen && <button className="danger-outline-button" disabled={completeMutation.isPending} onClick={() => { if (window.confirm('هل تريد إغلاق الزيارة؟ لن يمكن إضافة بيانات سريرية جديدة بعدها.')) completeMutation.mutate(); }}>إغلاق الزيارة</button>}</div>
      {encounterQuery.isError && <div className="alert alert-error">{errorMessage(encounterQuery.error)}</div>}
      {completeMutation.isError && <div className="alert alert-error">{errorMessage(completeMutation.error)}</div>}
      <div className="clinical-actions"><button onClick={() => setShowVitals(true)} disabled={!encounterIsOpen}>＋ تسجيل العلامات الحيوية</button><button onClick={() => setShowNote(true)} disabled={!encounterIsOpen}>＋ ملاحظة سريرية</button>{isDoctor && <><button onClick={() => setShowDiagnosis(true)} disabled={!encounterIsOpen}>＋ تشخيص</button><button onClick={() => setShowLabOrder(true)} disabled={!encounterIsOpen}>طلب فحص مختبري</button><button onClick={() => setShowPrescription(true)} disabled={!encounterIsOpen}>وصفة دوائية</button>{['INPATIENT','EMERGENCY'].includes(activeEncounter?.encounterType??'')&&<button onClick={() => setShowAdmission(true)} disabled={!encounterIsOpen}>بدء التنويم</button>}</>}</div>
      <div className="clinical-data-grid">
        <div className="clinical-data-card"><div className="card-heading"><h3>العلامات الحيوية</h3><span>{vitalsQuery.data?.length ?? 0}</span></div>{vitalsQuery.data?.length ? <div className="clinical-record-list">{vitalsQuery.data.map((v) => <div className="clinical-record" key={v.id}><strong>{dateLabel(v.measuredAt)}</strong><p>{v.temperatureC != null ? `الحرارة ${v.temperatureC}°C · ` : ''}{v.systolicMmhg != null ? `الضغط ${v.systolicMmhg}/${v.diastolicMmhg ?? '—'} · ` : ''}{v.pulseBpm != null ? `النبض ${v.pulseBpm} · ` : ''}{v.oxygenSaturation != null ? `الأكسجين ${v.oxygenSaturation}%` : ''}</p></div>)}</div> : <div className="empty-mini">لا توجد قياسات مسجلة.</div>}</div>
        <div className="clinical-data-card"><div className="card-heading"><h3>الملاحظات السريرية</h3><span>{notesQuery.data?.length ?? 0}</span></div>{notesQuery.data?.length ? <div className="clinical-record-list">{notesQuery.data.map((n) => <div className="clinical-record" key={n.id}><div className="record-title"><strong>{noteLabels[n.noteType] ?? n.noteType}</strong><span className={`appointment-status ${n.noteStatus === 'SIGNED' ? 'status-completed' : 'status-booked'}`}>{n.noteStatus === 'SIGNED' ? 'معتمدة' : 'مسودة'}</span></div><p>{n.noteText}</p>{n.noteStatus !== 'SIGNED' && <button className="text-action" disabled={!encounterIsOpen} onClick={async () => { try { await apiRequest(`/encounters/${encounterId}/notes/${n.id}/sign`, token!, { method: 'POST' }); await qc.invalidateQueries({ queryKey: ['encounter-notes', encounterId] }); setNotice('تم اعتماد الملاحظة.'); } catch (e) { setNotice(errorMessage(e)); } }}>اعتماد الملاحظة</button>}</div>)}</div> : <div className="empty-mini">لا توجد ملاحظات.</div>}</div>
        <div className="clinical-data-card"><div className="card-heading"><h3>التشخيصات</h3><span>{diagnosesQuery.data?.length ?? 0}</span></div>{diagnosesQuery.data?.length ? diagnosesQuery.data.map((d) => <div className="clinical-record" key={d.id}><strong>{d.diagnosisCode} · {d.description}</strong><p>{d.primary ? 'تشخيص رئيسي · ' : ''}{d.clinicalStatus}</p></div>) : <div className="empty-mini">لم تُسجل تشخيصات.</div>}</div>
        {isDoctor && <div className="clinical-data-card"><div className="card-heading"><h3>الوصفات الطبية</h3><span>{prescriptionsQuery.data?.length ?? 0}</span></div>{prescriptionsQuery.data?.length ? prescriptionsQuery.data.map((p) => <div className="clinical-record" key={p.id}><strong>وصفة · {p.status}</strong>{p.items.map((it) => <p key={it.id}>{it.medicationName} · {it.dose} · {it.frequency} · الكمية المتبقية {it.quantityRemaining}</p>)}</div>) : <div className="empty-mini">لا توجد وصفات مسجلة.</div>}</div>}
      </div>
      {[vitalsQuery,notesQuery,diagnosesQuery,prescriptionsQuery].some((q) => q.isError) && <div className="alert alert-error">تعذر تحميل بعض سجلات الزيارة. {errorMessage([vitalsQuery,notesQuery,diagnosesQuery,prescriptionsQuery].find((q) => q.isError)?.error)}</div>}
    </section>}
    {openEncounter && <div className="modal-backdrop" role="presentation"><div className="modal-card" role="dialog" aria-modal="true"><div className="modal-header"><div><h2>فتح زيارة طبية</h2><p>اربط الزيارة بمريض وقسم ونوع الزيارة.</p></div><button className="icon-button" onClick={() => setOpenEncounter(false)}>×</button></div><form className="form-grid" onSubmit={(e) => { e.preventDefault(); openMutation.mutate(); }}><label>القسم<select required value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}><option value="">اختر القسم</option>{departmentsQuery.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label><label>نوع الزيارة<select value={encounterType} onChange={(e) => setEncounterType(e.target.value)}><option value="OUTPATIENT">عيادة خارجية</option><option value="EMERGENCY">طوارئ</option><option value="FOLLOW_UP">متابعة</option><option value="INPATIENT">تنويم</option></select></label><label className="full-field">الشكوى الرئيسية<textarea value={chiefComplaint} onChange={(e) => setChiefComplaint(e.target.value)} maxLength={1000} rows={3}/></label>{openMutation.isError && <div className="alert alert-error full-field">{errorMessage(openMutation.error)}</div>}<div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={() => setOpenEncounter(false)}>إلغاء</button><button className="primary-button" disabled={!departmentId || openMutation.isPending}>فتح الزيارة</button></div></form></div></div>}
    {showVitals && <VitalModal token={token!} encounterId={encounterId} onClose={() => setShowVitals(false)} onSaved={async () => { setShowVitals(false); await qc.invalidateQueries({ queryKey: ['encounter-vitals', encounterId] }); setNotice('تم تسجيل العلامات الحيوية.'); }}/ >}
    {showNote && <NoteModal token={token!} encounterId={encounterId} onClose={() => setShowNote(false)} onSaved={async () => { setShowNote(false); await qc.invalidateQueries({ queryKey: ['encounter-notes', encounterId] }); setNotice('تم حفظ الملاحظة السريرية كمسودة.'); }}/ >}
    {showDiagnosis && <DiagnosisModal token={token!} encounterId={encounterId} onClose={() => setShowDiagnosis(false)} onSaved={async () => { setShowDiagnosis(false); await qc.invalidateQueries({ queryKey: ['encounter-diagnoses', encounterId] }); setNotice('تم تسجيل التشخيص.'); }}/ >}
    {showPrescription && <PrescriptionModal token={token!} encounterId={encounterId} onClose={() => setShowPrescription(false)} onSaved={async () => { setShowPrescription(false); await qc.invalidateQueries({ queryKey: ['encounter-prescriptions', encounterId] }); setNotice('تم حفظ الوصفة الطبية.'); }}/ >}
    {showLabOrder && <LabOrderModal token={token!} encounterId={encounterId} onClose={() => setShowLabOrder(false)} onSaved={async () => { setShowLabOrder(false); setNotice('تم إرسال طلب المختبر.'); }}/ >}
    {showAdmission && <AdmissionModal token={token!} branchId={branchId} encounterId={encounterId} onClose={() => setShowAdmission(false)} onSaved={async () => { setShowAdmission(false); setNotice('تم إنشاء حالة التنويم. افتح وحدة التنويم لتخصيص السرير وإدارة الإقامة.'); }}/ >}
  </div>;
}

function VitalModal({token,encounterId,onClose,onSaved}:{token:string;encounterId:string;onClose:()=>void;onSaved:()=>void}) {
 const [form,setForm]=useState({temperatureC:'',systolicMmhg:'',diastolicMmhg:'',pulseBpm:'',respiratoryRate:'',oxygenSaturation:'',weightKg:'',heightCm:'',notes:''});
 const mutation=useMutation({mutationFn:()=>apiRequest(`/encounters/${encounterId}/vitals`,token,{method:'POST',body:JSON.stringify(Object.fromEntries(Object.entries(form).map(([k,v])=>[k,v===''?undefined:(['notes'].includes(k)?v:Number(v))]))) }),onSuccess:onSaved});
 return <Modal title="تسجيل العلامات الحيوية" onClose={onClose}><form className="form-grid" onSubmit={e=>{e.preventDefault();mutation.mutate();}}>{[['temperatureC','الحرارة °C'],['systolicMmhg','الضغط الانقباضي'],['diastolicMmhg','الضغط الانبساطي'],['pulseBpm','النبض / دقيقة'],['respiratoryRate','التنفس / دقيقة'],['oxygenSaturation','الأكسجين %'],['weightKg','الوزن كجم'],['heightCm','الطول سم']].map(([key,label])=><label key={key}>{label}<input type="number" step="0.1" value={form[key as keyof typeof form]} onChange={e=>setForm({...form,[key]:e.target.value})}/></label>)}<label className="full-field">ملاحظات<textarea rows={2} value={form.notes} onChange={e=>setForm({...form,notes:e.target.value})}/></label><ModalError error={mutation.error}/><ModalActions onClose={onClose} pending={mutation.isPending} label="حفظ القياسات"/></form></Modal>;
}
function NoteModal({token,encounterId,onClose,onSaved}:{token:string;encounterId:string;onClose:()=>void;onSaved:()=>void}) {
 const [noteType,setNoteType]=useState('PROGRESS');const [noteText,setNoteText]=useState('');
 const mutation=useMutation({mutationFn:()=>apiRequest(`/encounters/${encounterId}/notes`,token,{method:'POST',body:JSON.stringify({noteType,noteText})}),onSuccess:onSaved});
 return <Modal title="إضافة ملاحظة سريرية" onClose={onClose}><form className="form-grid" onSubmit={e=>{e.preventDefault();mutation.mutate();}}><label className="full-field">نوع الملاحظة<select value={noteType} onChange={e=>setNoteType(e.target.value)}>{noteTypes.map(t=><option key={t} value={t}>{noteLabels[t]}</option>)}</select></label><label className="full-field">نص الملاحظة<textarea required minLength={3} maxLength={20000} rows={7} value={noteText} onChange={e=>setNoteText(e.target.value)}/></label><p className="form-hint full-field">تُحفظ الملاحظة كمسودة، ويجب اعتمادها من مستخدم مخول.</p><ModalError error={mutation.error}/><ModalActions onClose={onClose} pending={mutation.isPending} label="حفظ المسودة"/></form></Modal>;
}
function DiagnosisModal({token,encounterId,onClose,onSaved}:{token:string;encounterId:string;onClose:()=>void;onSaved:()=>void}) {
 const [q,setQ]=useState('');const [selected,setSelected]=useState<DiagnosisOption|null>(null);const [primary,setPrimary]=useState(true);const [clinicalStatus,setClinicalStatus]=useState('PROVISIONAL');
 const results=useQuery({queryKey:['diagnosis-catalog',q],queryFn:()=>apiRequest<DiagnosisOption[]>(`/diagnoses?q=${encodeURIComponent(q)}&limit=15`,token),enabled:q.trim().length>=2});
 const mutation=useMutation({mutationFn:()=>apiRequest(`/encounters/${encounterId}/diagnoses`,token,{method:'POST',body:JSON.stringify({diagnosisId:selected!.id,primary,clinicalStatus})}),onSuccess:onSaved});
 return <Modal title="إضافة تشخيص" onClose={onClose}><form className="form-grid" onSubmit={e=>{e.preventDefault();if(selected)mutation.mutate();}}><label className="full-field">ابحث في دليل التشخيصات<input minLength={2} value={q} onChange={e=>{setQ(e.target.value);setSelected(null);}} placeholder="رمز أو وصف التشخيص"/></label>{results.data?.map(d=><button type="button" className={`option-row ${selected?.id===d.id?'option-row-active':''}`} key={d.id} onClick={()=>setSelected(d)}><strong>{d.diagnosisCode}</strong><span>{d.description}</span></button>)}{selected&&<div className="selected-option full-field">التشخيص المختار: {selected.diagnosisCode} · {selected.description}</div>}<label><span>تصنيف الحالة</span><select value={clinicalStatus} onChange={e=>setClinicalStatus(e.target.value)}><option value="PROVISIONAL">مبدئي</option><option value="CONFIRMED">مؤكد</option><option value="RULED_OUT">مستبعد</option></select></label><label className="checkbox-field"><input type="checkbox" checked={primary} onChange={e=>setPrimary(e.target.checked)}/> تشخيص رئيسي</label><ModalError error={mutation.error}/><ModalActions onClose={onClose} pending={mutation.isPending} label="إضافة التشخيص" disabled={!selected}/></form></Modal>;
}
function PrescriptionModal({token,encounterId,onClose,onSaved}:{token:string;encounterId:string;onClose:()=>void;onSaved:()=>void}) {
 const [q,setQ]=useState('');const [selected,setSelected]=useState<Medication|null>(null);const [dose,setDose]=useState('');const [route,setRoute]=useState('ORAL');const [frequency,setFrequency]=useState('مرة يومياً');const [durationDays,setDurationDays]=useState('');const [quantity,setQuantity]=useState('');const [instructions,setInstructions]=useState('');
 const meds=useQuery({queryKey:['medication-search',q],queryFn:()=>apiRequest<Medication[]>(`/medications?q=${encodeURIComponent(q)}&limit=15`,token),enabled:q.trim().length>=2});
 const mutation=useMutation({mutationFn:()=>apiRequest(`/encounters/${encounterId}/prescriptions`,token,{method:'POST',body:JSON.stringify({items:[{medicationId:selected!.id,dose,route,frequency,durationDays:durationDays?Number(durationDays):undefined,quantityPrescribed:Number(quantity),instructions:instructions||undefined}]})}),onSuccess:onSaved});
 return <Modal title="إنشاء وصفة طبية" onClose={onClose}><form className="form-grid" onSubmit={e=>{e.preventDefault();if(selected)mutation.mutate();}}><label className="full-field">ابحث عن الدواء<input minLength={2} value={q} onChange={e=>{setQ(e.target.value);setSelected(null);}} placeholder="الاسم أو الرمز"/></label>{meds.data?.filter(m=>!m.controlled).map(m=><button type="button" key={m.id} className={`option-row ${selected?.id===m.id?'option-row-active':''}`} onClick={()=>setSelected(m)}><strong>{m.genericName} {m.strength||''}</strong><span>{m.code} · {m.dispensingUnit} {m.brandName?`· ${m.brandName}`:''}</span></button>)}{selected&&<div className="selected-option full-field">{selected.genericName} · {selected.strength} · {selected.dispensingUnit}</div>}<label>الجرعة<input required value={dose} onChange={e=>setDose(e.target.value)} placeholder="مثال: قرص واحد"/></label><label>طريقة الاستخدام<select value={route} onChange={e=>setRoute(e.target.value)}><option value="ORAL">عن طريق الفم</option><option value="TOPICAL">موضعي</option><option value="INHALATION">استنشاق</option><option value="INJECTION">حقن</option><option value="OTHER">أخرى</option></select></label><label>التكرار<input required value={frequency} onChange={e=>setFrequency(e.target.value)} placeholder="كل 8 ساعات"/></label><label>مدة العلاج بالأيام<input type="number" min="1" max="365" value={durationDays} onChange={e=>setDurationDays(e.target.value)}/></label><label>الكمية الموصوفة<input required type="number" min="0.001" step="0.001" value={quantity} onChange={e=>setQuantity(e.target.value)}/></label><label className="full-field">تعليمات<textarea rows={2} value={instructions} onChange={e=>setInstructions(e.target.value)}/></label><ModalError error={mutation.error}/><ModalActions onClose={onClose} pending={mutation.isPending} label="حفظ الوصفة" disabled={!selected}/></form></Modal>;
}
function LabOrderModal({token,encounterId,onClose,onSaved}:{token:string;encounterId:string;onClose:()=>void;onSaved:()=>void}) {
 const [q,setQ]=useState('');const [selected,setSelected]=useState<LabTest[]>([]);const [priority,setPriority]=useState('ROUTINE');const [clinicalQuestion,setClinicalQuestion]=useState('');
 const tests=useQuery({queryKey:['lab-test-search',q],queryFn:()=>apiRequest<LabTest[]>(`/lab-tests?q=${encodeURIComponent(q)}&limit=15`,token),enabled:q.trim().length>=2});
 const mutation=useMutation({mutationFn:()=>apiRequest(`/encounters/${encounterId}/lab-orders`,token,{method:'POST',body:JSON.stringify({priority,clinicalQuestion:clinicalQuestion||undefined,testIds:selected.map(t=>t.id)})}),onSuccess:onSaved});
 return <Modal title="طلب فحوصات مختبرية" onClose={onClose}><form className="form-grid" onSubmit={e=>{e.preventDefault();mutation.mutate();}}><label className="full-field">ابحث عن الفحص<input minLength={2} value={q} onChange={e=>setQ(e.target.value)} placeholder="اسم أو رمز التحليل"/></label>{tests.data?.map(t=><button type="button" className={`option-row ${selected.some(s=>s.id===t.id)?'option-row-active':''}`} key={t.id} onClick={()=>setSelected(old=>old.some(s=>s.id===t.id)?old.filter(s=>s.id!==t.id):[...old,t])}><strong>{t.name}</strong><span>{t.code} · {t.specimenType} · {t.standardPrice} {t.currencyCode}</span></button>)}{selected.length>0&&<div className="selected-option full-field">الفحوصات المختارة: {selected.map(s=>s.name).join('، ')}</div>}<label>الأولوية<select value={priority} onChange={e=>setPriority(e.target.value)}><option value="ROUTINE">عادي</option><option value="URGENT">عاجل</option><option value="STAT">فوري</option></select></label><label className="full-field">السؤال السريري<textarea rows={2} value={clinicalQuestion} onChange={e=>setClinicalQuestion(e.target.value)}/></label><ModalError error={mutation.error}/><ModalActions onClose={onClose} pending={mutation.isPending} label="إرسال طلب المختبر" disabled={!selected.length}/></form></Modal>;
}
function AdmissionModal({token,branchId,encounterId,onClose,onSaved}:{token:string;branchId:string;encounterId:string;onClose:()=>void;onSaved:()=>void}) {
 const [admissionType,setAdmissionType]=useState('ELECTIVE');const [admissionReason,setAdmissionReason]=useState('');const [initialBedId,setInitialBedId]=useState('');const [bedAssignmentReason,setBedAssignmentReason]=useState('');
 const beds=useQuery({queryKey:['admission-beds',branchId,true],queryFn:()=>apiRequest<BedOption[]>(`/admissions/beds?branchId=${branchId}&availableOnly=true`,token)});
 const mutation=useMutation({mutationFn:()=>apiRequest('/admissions',token,{method:'POST',body:JSON.stringify({branchId,encounterId,admissionType,admissionReason:admissionReason||undefined,initialBedId:initialBedId||undefined,bedAssignmentReason:bedAssignmentReason||undefined})}),onSuccess:onSaved});
 return <Modal title="بدء إجراءات التنويم" onClose={onClose}><form className="form-grid" onSubmit={e=>{e.preventDefault();mutation.mutate();}}><label>نوع التنويم<select value={admissionType} onChange={e=>setAdmissionType(e.target.value)}><option value="ELECTIVE">مجدول</option><option value="EMERGENCY">طارئ</option><option value="TRANSFER">تحويل</option><option value="OBSERVATION">ملاحظة</option></select></label><label className="full-field">سبب التنويم<textarea rows={3} maxLength={1000} value={admissionReason} onChange={e=>setAdmissionReason(e.target.value)}/></label><label className="full-field">سرير مبدئي (اختياري)<select value={initialBedId} onChange={e=>setInitialBedId(e.target.value)}><option value="">إنشاء التنويم دون تخصيص سرير</option>{beds.data?.map(b=><option key={b.id} value={b.id}>{b.wardName} · غرفة {b.roomNumber} · سرير {b.bedNumber} · {b.bedType}</option>)}</select></label><label className="full-field">سبب تخصيص السرير<textarea rows={2} value={bedAssignmentReason} onChange={e=>setBedAssignmentReason(e.target.value)}/></label>{beds.isError&&<div className="alert alert-error full-field">{errorMessage(beds.error)}</div>}<ModalError error={mutation.error}/><ModalActions onClose={onClose} pending={mutation.isPending} label="إنشاء حالة التنويم"/></form></Modal>;
}

function Modal({title,onClose,children}:{title:string;onClose:()=>void;children:ReactNode}) { return <div className="modal-backdrop"><div className="modal-card" role="dialog" aria-modal="true"><div className="modal-header"><div><h2>{title}</h2><p>Smart Hospital · مساحة العمل السريرية</p></div><button className="icon-button" onClick={onClose}>×</button></div>{children}</div></div>; }
function ModalError({error}:{error:unknown}) { return error ? <div className="alert alert-error full-field">{errorMessage(error)}</div> : null; }
function ModalActions({onClose,pending,label,disabled=false}:{onClose:()=>void;pending:boolean;label:string;disabled?:boolean}) { return <div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={onClose}>إلغاء</button><button className="primary-button" disabled={pending||disabled}>{pending?'جارٍ الحفظ…':label}</button></div>; }

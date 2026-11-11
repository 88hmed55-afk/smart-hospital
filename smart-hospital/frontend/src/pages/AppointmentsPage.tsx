import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { CalendarPlus, CalendarRange } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { AppointmentItem, Department, DoctorOption, PageResponse, PatientSummary } from '../types/api';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
const today = localDate(new Date());
const tomorrowDate = new Date(); tomorrowDate.setDate(tomorrowDate.getDate() + 1);
const tomorrow = localDate(tomorrowDate);
const statusLabels: Record<string, string> = {
  BOOKED: 'محجوز', CONFIRMED: 'مؤكد', CHECKED_IN: 'وصل', COMPLETED: 'مكتمل', CANCELLED: 'ملغي', NO_SHOW: 'لم يحضر',
};
function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('ar-YE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
function localDateTimeToIso(date: string, time: string) { return new Date(`${date}T${time}:00`).toISOString(); }

const appointmentExportColumns: ExportColumn<AppointmentItem>[] = [
  { header: 'الموعد', value: (row) => formatDateTime(row.startsAt), width: 1.4 },
  { header: 'المريض', value: (row) => row.patientName, width: 1.7 },
  { header: 'رقم الملف', value: (row) => row.patientMrn, width: 1 },
  { header: 'الطبيب', value: (row) => row.doctorName, width: 1.6 },
  { header: 'القسم', value: (row) => row.departmentName, width: 1.2 },
  { header: 'السبب', value: (row) => row.reason || '—', width: 1.6 },
  { header: 'الحالة', value: (row) => statusLabels[row.status] ?? row.status, width: 0.9, align: 'center' },
];

export default function AppointmentsPage() {
  const { token, user } = useAuth();
  const { branchId, branch } = useOutletContext<LayoutOutletContext>();
  const queryClient = useQueryClient();
  const [fromDate, setFromDate] = useState(today);
  const [toDate, setToDate] = useState(tomorrow);
  const [bookingOpen, setBookingOpen] = useState(false);
  const canManage = user?.roleCodes.some((role) => ['HOSPITAL_ADMIN', 'RECEPTIONIST'].includes(role)) ?? false;
  const from = localDateTimeToIso(fromDate, '00:00');
  const to = localDateTimeToIso(toDate, '00:00');

  const appointmentsQuery = useQuery({
    queryKey: ['appointments', branchId, from, to],
    queryFn: () => apiRequest<PageResponse<AppointmentItem>>(`/appointments?branchId=${encodeURIComponent(branchId)}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&offset=0&limit=100`, token!),
    enabled: Boolean(token && branchId && toDate > fromDate),
  });
  const checkInMutation = useMutation({
    mutationFn: (appointmentId: string) => apiRequest<AppointmentItem>(`/appointments/${appointmentId}/check-in`, token!, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments', branchId] }),
  });

  return (
    <div className="module-page">
      <PageHeader
        eyebrow="APPOINTMENT & RECEPTION"
        title="المواعيد والاستقبال"
        description="تنظيم مواعيد العيادات، متابعة وصول المرضى، وتسجيل الحضور في الفرع المحدد."
        icon={CalendarRange}
        meta={[`${fromDate} ← ${toDate}`]}
        actions={
          <>
            <ExportMenu
              fileName="جدول-المواعيد"
              title="تقرير جدول المواعيد"
              subtitle={`المواعيد من ${fromDate} إلى ${toDate}`}
              meta={[
                { label: 'الفرع', value: branch?.name ?? 'الفرع الحالي' },
                { label: 'الفترة', value: `${fromDate} — ${toDate}` },
              ]}
              columns={appointmentExportColumns}
              rows={appointmentsQuery.data?.items ?? []}
            />
            {canManage && (
              <button className="primary-button" onClick={() => setBookingOpen(true)}>
                <CalendarPlus size={16} /> حجز موعد
              </button>
            )}
          </>
        }
      />
      <section className="panel appointment-panel">
        <div className="appointment-toolbar">
          <div className="date-range"><label>من تاريخ<input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} /></label><label>إلى تاريخ<input type="date" value={toDate} min={fromDate} onChange={(e) => setToDate(e.target.value)} /></label></div>
          <div className="schedule-caption"><span className="online-dot" />{branch?.name ?? 'الفرع الحالي'} · جدول المواعيد</div>
        </div>
        {toDate <= fromDate && <div className="alert alert-error">يجب أن يكون تاريخ النهاية بعد تاريخ البداية.</div>}
        {appointmentsQuery.isError && <div className="alert alert-error">{appointmentsQuery.error instanceof ApiError ? appointmentsQuery.error.message : 'تعذر تحميل جدول المواعيد.'}</div>}
        {appointmentsQuery.isLoading ? <div className="empty-state">جارٍ تحميل جدول المواعيد…</div> : appointmentsQuery.data?.items.length ? (
          <div className="table-wrap"><table className="data-table appointment-table"><thead><tr><th>الموعد</th><th>المريض</th><th>الطبيب</th><th>القسم</th><th>السبب</th><th>الحالة</th><th>الإجراء</th></tr></thead>
            <tbody>{appointmentsQuery.data.items.map((item) => <tr key={item.id}>
              <td><strong>{formatDateTime(item.startsAt)}</strong></td>
              <td><div className="stacked-cell"><strong>{item.patientName}</strong><small>{item.patientMrn}</small></div></td>
              <td>{item.doctorName}</td><td>{item.departmentName}</td><td className="reason-cell">{item.reason || '—'}</td>
              <td><span className={`appointment-status status-${item.status.toLowerCase()}`}>{statusLabels[item.status] ?? item.status}</span></td>
              <td>{canManage && ['BOOKED', 'CONFIRMED'].includes(item.status)
                ? <button className="checkin-button" disabled={checkInMutation.isPending} onClick={() => checkInMutation.mutate(item.id)}>تسجيل الوصول</button>
                : <span className="muted-text">—</span>}</td>
            </tr>)}</tbody></table></div>
        ) : <div className="empty-state"><span>▣</span><strong>لا توجد مواعيد في الفترة المحددة</strong><small>{canManage ? 'يمكنك إنشاء حجز جديد للفرع الحالي.' : 'غيّر الفترة الزمنية أو راجع جدول الطبيب.'}</small></div>}
        {appointmentsQuery.data && <div className="table-footer"><span>إجمالي المواعيد: {appointmentsQuery.data.total.toLocaleString('ar-YE')}</span><span>تعرض الصفحة حتى 100 موعد من الفترة المختارة.</span></div>}
        {checkInMutation.isError && <div className="alert alert-error">{checkInMutation.error instanceof Error ? checkInMutation.error.message : 'تعذر تسجيل وصول المريض.'}</div>}
      </section>
      {bookingOpen && <AppointmentBookingModal token={token!} branchId={branchId} onClose={() => setBookingOpen(false)} onCreated={async () => { setBookingOpen(false); await queryClient.invalidateQueries({ queryKey: ['appointments', branchId] }); }} />}
    </div>
  );
}

function AppointmentBookingModal({ token, branchId, onClose, onCreated }: { token: string; branchId: string; onClose: () => void; onCreated: () => void }) {
  const [departmentId, setDepartmentId] = useState('');
  const [doctorStaffId, setDoctorStaffId] = useState('');
  const [patientSearch, setPatientSearch] = useState('');
  const [searchSubmitted, setSearchSubmitted] = useState('');
  const [patientId, setPatientId] = useState('');
  const [patientLabel, setPatientLabel] = useState('');
  const [date, setDate] = useState(today);
  const [startTime, setStartTime] = useState('09:00');
  const [duration, setDuration] = useState('30');
  const [reason, setReason] = useState('');
  const [formError, setFormError] = useState('');

  const departmentsQuery = useQuery({
    queryKey: ['departments', branchId],
    queryFn: () => apiRequest<Department[]>(`/organization/branches/${branchId}/departments`, token),
  });
  const doctorsQuery = useQuery({
    queryKey: ['doctors', branchId, departmentId],
    queryFn: () => apiRequest<DoctorOption[]>(`/organization/branches/${branchId}/doctors${departmentId ? `?departmentId=${departmentId}` : ''}`, token),
    enabled: Boolean(departmentId),
  });
  const patientsQuery = useQuery({
    queryKey: ['patient-picker', searchSubmitted],
    queryFn: () => apiRequest<PageResponse<PatientSummary>>(`/patients?q=${encodeURIComponent(searchSubmitted)}&offset=0&limit=8`, token),
    enabled: Boolean(searchSubmitted.trim()),
  });
  const bookMutation = useMutation({
    mutationFn: () => {
      const startsAt = localDateTimeToIso(date, startTime);
      const endsAt = new Date(new Date(startsAt).getTime() + Number(duration) * 60_000).toISOString();
      return apiRequest<AppointmentItem>('/appointments', token, { method: 'POST', body: JSON.stringify({ branchId, patientId, doctorStaffId, departmentId, startsAt, endsAt, reason: reason.trim() || undefined }) });
    },
    onSuccess: () => { void onCreated(); },
  });
  function searchPatient() { setSearchSubmitted(patientSearch.trim()); setPatientId(''); setPatientLabel(''); }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setFormError('');
    if (!patientId || !departmentId || !doctorStaffId) { setFormError('اختر المريض والقسم والطبيب أولاً.'); return; }
    if (date < today) { setFormError('لا يمكن إنشاء موعد بتاريخ سابق.'); return; }
    bookMutation.mutate();
  }
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="modal-card appointment-modal" role="dialog" aria-modal="true" aria-labelledby="booking-title">
      <div className="modal-heading"><div><span className="eyebrow">NEW APPOINTMENT</span><h2 id="booking-title">إنشاء حجز موعد</h2><p>اختر مريضاً لديه ملف وصلاحية وصول في هذا الفرع.</p></div><button className="modal-close" onClick={onClose} aria-label="إغلاق">×</button></div>
      <form onSubmit={submit} className="modal-form">
        <div className="patient-picker-box"><strong>1. تحديد المريض</strong><div className="picker-search"><input value={patientSearch} onChange={(e) => setPatientSearch(e.target.value)} placeholder="ابحث برقم الملف أو الاسم" required /><button type="button" className="secondary-button" onClick={searchPatient}>بحث</button></div>
          {patientsQuery.isError && <small className="field-error">تعذر البحث في ملفات المرضى.</small>}
          {patientsQuery.data?.items.map((patient) => <button type="button" className={`patient-option ${patientId === patient.id ? 'patient-option-selected' : ''}`} key={patient.id} onClick={() => { setPatientId(patient.id); setPatientLabel(`${patient.firstName} ${patient.lastName} · ${patient.mrn}`); }}><span>{patient.firstName} {patient.lastName}</span><small>{patient.mrn}</small><b>{patientId === patient.id ? '✓' : 'اختيار'}</b></button>)}
          {patientId && <div className="selected-patient">المريض المحدد: <strong>{patientLabel}</strong></div>}
        </div>
        <strong className="form-step-title">2. القسم والطبيب</strong>
        <div className="form-grid two-cols"><label>القسم<select required value={departmentId} onChange={(e) => { setDepartmentId(e.target.value); setDoctorStaffId(''); }}><option value="">اختر القسم</option>{departmentsQuery.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select>{departmentsQuery.isError && <small className="field-error">تعذر تحميل الأقسام.</small>}</label>
          <label>الطبيب<select required value={doctorStaffId} onChange={(e) => setDoctorStaffId(e.target.value)} disabled={!departmentId || doctorsQuery.isLoading}><option value="">اختر الطبيب</option>{doctorsQuery.data?.map((doctor) => <option key={doctor.staffId} value={doctor.staffId}>{doctor.fullName} · {doctor.specialty}</option>)}</select>{departmentId && doctorsQuery.data?.length === 0 && !doctorsQuery.isLoading && <small className="field-error">لا يوجد طبيب نشط لهذا القسم.</small>}</label></div>
        <strong className="form-step-title">3. الوقت والتفاصيل</strong>
        <div className="form-grid three-cols"><label>التاريخ<input type="date" min={today} required value={date} onChange={(e) => setDate(e.target.value)} /></label><label>وقت البداية<input type="time" required value={startTime} onChange={(e) => setStartTime(e.target.value)} /></label><label>المدة<select value={duration} onChange={(e) => setDuration(e.target.value)}><option value="15">15 دقيقة</option><option value="30">30 دقيقة</option><option value="45">45 دقيقة</option><option value="60">ساعة</option></select></label></div>
        <label className="full-label">سبب الزيارة (اختياري)<textarea maxLength={500} rows={2} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="سبب الحجز أو ملاحظة الاستقبال" /></label>
        {(formError || bookMutation.isError) && <div className="alert alert-error">{formError || (bookMutation.error instanceof Error ? bookMutation.error.message : 'تعذر حجز الموعد.')}</div>}
        <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>إلغاء</button><button className="primary-button" disabled={bookMutation.isPending}>{bookMutation.isPending ? 'جارٍ إنشاء الحجز…' : 'تأكيد حجز الموعد'}</button></div>
      </form>
    </section>
  </div>;
}

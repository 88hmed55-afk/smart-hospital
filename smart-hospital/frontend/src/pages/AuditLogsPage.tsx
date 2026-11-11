import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { RefreshCw, ShieldCheck } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { AuditLogItem, PageResponse } from '../types/api';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

const errorText = (error: unknown) => error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'تعذر تحميل سجل التدقيق.';
const dateText = (value: string) => new Intl.DateTimeFormat('ar-YE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
const outcomeLabel: Record<AuditLogItem['outcome'], string> = { SUCCESS: 'نجاح', DENIED: 'مرفوض', FAILURE: 'فشل' };

const auditExportColumns: ExportColumn<AuditLogItem>[] = [
  { header: 'التاريخ والوقت', value: (row) => dateText(row.occurredAt), width: 1.3 },
  { header: 'المستخدم', value: (row) => row.actorUsername, width: 1.1 },
  { header: 'العملية', value: (row) => row.actionCode, width: 1.6, align: 'left' },
  { header: 'نوع السجل', value: (row) => row.resourceType, width: 1.1, align: 'left' },
  { header: 'المعرّف المرجعي', value: (row) => row.resourceId ?? '—', width: 1.4, align: 'left' },
  { header: 'الفرع', value: (row) => row.branchName ?? '—', width: 1.1 },
  { header: 'النتيجة', value: (row) => outcomeLabel[row.outcome], width: 0.8, align: 'center' },
];

export default function AuditLogsPage() {
  const { token } = useAuth();
  const { branchId, branch } = useOutletContext<LayoutOutletContext>();
  const [actionCode, setActionCode] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [offset, setOffset] = useState(0);
  const limit = 30;
  const query = useQuery({
    queryKey: ['audit-logs', branchId, actionCode, resourceType, offset],
    queryFn: () => {
      const params = new URLSearchParams({ branchId, offset: String(offset), limit: String(limit) });
      if (actionCode.trim()) params.set('actionCode', actionCode.trim());
      if (resourceType) params.set('resourceType', resourceType);
      return apiRequest<PageResponse<AuditLogItem>>(`/audit-logs?${params.toString()}`, token!);
    },
    enabled: Boolean(token && branchId),
  });
  const page = query.data;

  return <div className="module-page governance-page">
    <PageHeader eyebrow="SECURITY & COMPLIANCE" title="سجل التدقيق والعمليات" description="سجل زمني غير قابل للتعديل للعمليات الحساسة ضمن نطاق وصول المستخدم." icon={ShieldCheck} meta={[`${page?.total ?? 0} سجل مطابق`]} actions={<>
      <ExportMenu fileName="سجل-التدقيق" title="تقرير سجل التدقيق" subtitle={`العمليات المسجلة — ${branch?.name ?? 'الفرع الحالي'}`} meta={[{ label: 'رمز العملية', value: actionCode || 'الكل' }, { label: 'نوع المورد', value: resourceType || 'الكل' }]} columns={auditExportColumns} rows={page?.items ?? []} />
      <button className="secondary-button" onClick={() => void query.refetch()} disabled={query.isFetching}><RefreshCw size={15} className={query.isFetching ? 'spin' : undefined} /> تحديث السجل</button>
    </>} />
    <section className="panel audit-filters">
      <label>رمز العملية<input value={actionCode} onChange={(event) => { setActionCode(event.target.value); setOffset(0); }} placeholder="مثال: PATIENT_RECORD_VIEWED" /></label>
      <label>نوع المورد<select value={resourceType} onChange={(event) => { setResourceType(event.target.value); setOffset(0); }}><option value="">كل الأنواع</option>{['PATIENT','APPOINTMENT','ENCOUNTER','LAB_ORDER','LAB_RESULT','PRESCRIPTION','DISPENSE','INVOICE','PAYMENT','JOURNAL_ENTRY','PURCHASE_ORDER','GOODS_RECEIPT','USER_ACCOUNT','STAFF_MEMBER','SYSTEM_SETTING','ADMISSION'].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
      <div className="audit-count"><strong>{page?.total ?? 0}</strong><span>سجل مطابق</span></div>
    </section>
    {query.isError && <div className="alert alert-error">{errorText(query.error)}</div>}
    <section className="panel audit-table-panel">
      {query.isLoading ? <div className="empty-state">جارٍ تحميل سجل التدقيق…</div> : page?.items.length ? <>
        <div className="table-wrap"><table className="data-table audit-table"><thead><tr><th>التاريخ والوقت</th><th>المستخدم</th><th>العملية</th><th>نوع السجل</th><th>المعرّف المرجعي</th><th>النتيجة</th></tr></thead><tbody>
          {page.items.map((item) => <tr key={item.id}><td dir="ltr">{dateText(item.occurredAt)}</td><td><strong>{item.actorUsername}</strong></td><td><code dir="ltr">{item.actionCode}</code></td><td>{item.resourceType}</td><td><code dir="ltr">{item.resourceId ? `${item.resourceId.slice(0, 8)}…` : '—'}</code></td><td><span className={`audit-outcome audit-outcome-${item.outcome.toLowerCase()}`}>{outcomeLabel[item.outcome]}</span></td></tr>)}
        </tbody></table></div>
        <div className="audit-pagination"><span>عرض {offset + 1}–{Math.min(offset + page.items.length, page.total)} من {page.total}</span><div><button className="secondary-button" disabled={offset === 0 || query.isFetching} onClick={() => setOffset(Math.max(0, offset - limit))}>السابق</button><button className="secondary-button" disabled={offset + limit >= page.total || query.isFetching} onClick={() => setOffset(offset + limit)}>التالي</button></div></div>
      </> : <div className="empty-state"><strong>لا توجد سجلات مطابقة</strong><small>ستظهر هنا العمليات التي يسجلها النظام مع مرور الوقت.</small></div>}
    </section>
    <p className="governance-note">يعرض السجل بيانات وصفية للعمليات ولا يعرض نصوص الملاحظات الطبية أو تفاصيل نتائج المرضى. سجل قاعدة البيانات محمي من التعديل والحذف في إصدار V011.</p>
  </div>;
}

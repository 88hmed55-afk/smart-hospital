import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { LayoutDashboard, RefreshCw } from 'lucide-react';
import { apiRequest, ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { DashboardMetrics, DecisionCard, DecisionCardStatus } from '../types/api';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

function number(value: number | undefined, digits = 0) {
  if (value === undefined || value === null) return '—';
  return new Intl.NumberFormat('ar-YE', { maximumFractionDigits: digits }).format(value);
}
function currency(value: number, code: string) {
  try { return new Intl.NumberFormat('ar-YE', { style: 'currency', currency: code, maximumFractionDigits: 0 }).format(value); }
  catch { return `${number(value)} ${code}`; }
}
function timestamp(value?: string) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('ar-YE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

const metricDefinitions = [
  { key: 'appointmentsToday', label: 'مواعيد اليوم', hint: 'مواعيد مجدولة في هذا الفرع', mark: '▣', tone: 'blue' },
  { key: 'visitsToday', label: 'الزيارات الطبية', hint: 'زيارات بدأت اليوم', mark: '✚', tone: 'teal' },
  { key: 'activeAdmissions', label: 'حالات التنويم', hint: 'حالات تنويم نشطة', mark: '▥', tone: 'violet' },
  { key: 'pendingLabItems', label: 'عناصر مختبر معلّقة', hint: 'تحتاج إلى استكمال المعالجة', mark: '⚗', tone: 'amber' },
] as const;

type DashboardExportRow = { metric: string; value: string };

const dashboardExportColumns: ExportColumn<DashboardExportRow>[] = [
  { header: 'المؤشر', value: (row) => row.metric, width: 2 },
  { header: 'القيمة', value: (row) => row.value, width: 1, align: 'left' },
];

export default function DashboardPage() {
  const { token, user } = useAuth();
  const { branchId, branch } = useOutletContext<LayoutOutletContext>();
  const queryClient = useQueryClient();
  const manager = user?.roleCodes.some((role) => ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER'].includes(role)) ?? false;

  const dashboardQuery = useQuery({
    queryKey: ['dashboard', branchId],
    queryFn: () => apiRequest<DashboardMetrics>(`/analytics/dashboard?branchId=${encodeURIComponent(branchId)}`, token!),
    enabled: Boolean(token && branchId),
  });
  const cardsQuery = useQuery({
    queryKey: ['decision-cards', branchId],
    queryFn: () => apiRequest<DecisionCard[]>(`/analytics/decision-cards?branchId=${encodeURIComponent(branchId)}&openOnly=true&limit=10`, token!),
    enabled: Boolean(token && branchId && manager),
  });
  const refreshMutation = useMutation({
    mutationFn: () => apiRequest<DashboardMetrics>(`/analytics/dashboard/refresh?branchId=${encodeURIComponent(branchId)}`, token!, { method: 'POST' }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['dashboard', branchId] }),
        queryClient.invalidateQueries({ queryKey: ['decision-cards', branchId] }),
      ]);
    },
  });
  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: DecisionCardStatus }) => apiRequest<DecisionCard>(`/analytics/decision-cards/${id}/status`, token!, { method: 'PATCH', body: JSON.stringify({ status }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['decision-cards', branchId] }),
  });

  const metrics = dashboardQuery.data;
  const cards = cardsQuery.data ?? [];
  const labProgress = metrics && metrics.labTurnaroundSamples > 0
    ? `${number(metrics.labTurnaroundSamples)} عينة محتسبة اليوم`
    : 'لا توجد عينات مكتملة بزمن صالح اليوم';
  const exportRows: DashboardExportRow[] = metrics
    ? [
        { metric: 'مواعيد اليوم', value: number(metrics.appointmentsToday) },
        { metric: 'الزيارات الطبية', value: number(metrics.visitsToday) },
        { metric: 'المواعيد المؤكدة الحضور', value: number(metrics.checkedInAppointments) },
        { metric: 'حالات التنويم النشطة', value: number(metrics.activeAdmissions) },
        { metric: 'نسبة إشغال الأسرة', value: `${number(Number(metrics.bedOccupancyPercent), 1)}%` },
        { metric: 'الأسرة المشغولة', value: `${number(metrics.occupiedBeds)} من ${number(metrics.operationalBeds)}` },
        { metric: 'عناصر مختبر معلّقة', value: number(metrics.pendingLabItems) },
        { metric: 'متوسط زمن إنجاز المختبر (دقيقة)', value: number(Number(metrics.averageLabTurnaroundMinutes), 1) },
        { metric: 'أصناف دوائية تحت حد الطلب', value: number(metrics.lowStockMedicationPolicies) },
        { metric: 'الإيرادات المحصلة اليوم', value: currency(Number(metrics.postedRevenueToday ?? 0), metrics.currencyCode || 'YER') },
        { metric: 'بطاقات قرار مفتوحة', value: number(metrics.openDecisionCards) },
      ]
    : [];

  return (
    <div className="dashboard-page">
      <PageHeader
        eyebrow="OPS · LIVE OVERVIEW"
        title="لوحة القيادة"
        description="صورة تشغيلية موحدة لمؤشرات المستشفى في الفرع المحدد، مع بطاقات قرار مبنية على عتبات واضحة."
        icon={LayoutDashboard}
        meta={[metrics ? `الفترة: ${timestamp(metrics.periodStart)}` : 'مؤشرات اليوم']}
        actions={
          <>
            <ExportMenu
              fileName="لوحة-القيادة"
              title="تقرير لوحة القيادة التشغيلية"
              subtitle={`ملخص مؤشرات التشغيل — ${branch?.name ?? 'الفرع الحالي'}`}
              meta={[
                { label: 'الفرع', value: branch?.name ?? 'الفرع الحالي' },
                { label: 'الفترة', value: metrics ? timestamp(metrics.periodStart) : 'اليوم' },
              ]}
              columns={dashboardExportColumns}
              rows={exportRows}
              disabled={!metrics}
            />
            {manager && (
              <button className="secondary-button" onClick={() => refreshMutation.mutate()} disabled={!branchId || refreshMutation.isPending}>
                <RefreshCw size={15} className={refreshMutation.isPending ? 'spin' : undefined} />
                {refreshMutation.isPending ? 'جارٍ تحديث اللقطة…' : 'تحديث المؤشرات'}
              </button>
            )}
          </>
        }
      />

      {dashboardQuery.isError && <div className="alert alert-error">{dashboardQuery.error instanceof ApiError ? dashboardQuery.error.message : 'تعذر تحميل مؤشرات لوحة القيادة.'}</div>}
      {refreshMutation.isError && <div className="alert alert-error">تعذر تحديث اللقطة التشغيلية. راجع صلاحية المدير وسجل الخادم.</div>}
      {refreshMutation.isSuccess && <div className="alert alert-success">تم تنفيذ طلب تحديث المؤشرات والبطاقات التشغيلية.</div>}

      <div className="section-title"><div><h2>المؤشرات الرئيسية</h2><p>{branch?.name ?? 'الفرع الحالي'} · أرقام مجمّعة من قاعدة البيانات</p></div><span className="data-live"><i /> بيانات النظام</span></div>
      <div className="metrics-grid">
        {metricDefinitions.map((item) => (
          <article className="metric-card" key={item.key}>
            <div className={`metric-icon metric-${item.tone}`}>{item.mark}</div>
            <div className="metric-label">{item.label}</div>
            <div className="metric-value">{dashboardQuery.isLoading ? <span className="skeleton" /> : number(metrics?.[item.key])}</div>
            <div className="metric-hint">{item.hint}</div>
          </article>
        ))}
      </div>

      <div className="insights-grid">
        <section className="panel occupancy-panel">
          <div className="panel-heading"><div><h2>إشغال الأسرة</h2><p>الأسرة التشغيلية المشغولة حالياً</p></div><span className="panel-mark">▥</span></div>
          <div className="occupancy-value">{dashboardQuery.isLoading ? '—' : `${number(Number(metrics?.bedOccupancyPercent ?? 0), 1)}%`}<small>نسبة الإشغال</small></div>
          <div className="progress-track"><span style={{ width: `${Math.min(Math.max(Number(metrics?.bedOccupancyPercent ?? 0), 0), 100)}%` }} /></div>
          <div className="progress-meta"><span>{number(metrics?.occupiedBeds)} سرير مشغول</span><span>{number(metrics?.operationalBeds)} سرير تشغيلي</span></div>
        </section>
        <section className="panel finance-panel">
          <div className="panel-heading"><div><h2>الإيرادات المحصلة</h2><p>المدفوعات المرحلة خلال اليوم</p></div><span className="panel-mark">◫</span></div>
          <div className="finance-value">{dashboardQuery.isLoading ? '—' : currency(Number(metrics?.postedRevenueToday ?? 0), metrics?.currencyCode || 'YER')}</div>
          <div className="finance-note"><span className="finance-dot" /> إجمالي الدفعات بحالة POSTED فقط</div>
        </section>
        <section className="panel lab-panel">
          <div className="panel-heading"><div><h2>أداء المختبر</h2><p>متوسط زمن الإنجاز للعناصر المعتمدة اليوم</p></div><span className="panel-mark">⚗</span></div>
          <div className="lab-value">{dashboardQuery.isLoading ? '—' : number(Number(metrics?.averageLabTurnaroundMinutes ?? 0), 1)}<small>دقيقة</small></div>
          <div className="finance-note">{labProgress}</div>
        </section>
      </div>

      <section className="panel intelligence-panel">
        <div className="panel-heading intelligence-heading"><div><span className="eyebrow">SMART OPERATIONS</span><h2>مركز التنبيهات وبطاقات القرار</h2><p>تنبيهات تشغيلية مبنية على عتبات واضحة وأدلة قابلة للمراجعة، وليست توصيات تشخيص أو علاج.</p></div><div className="decision-count">{number(metrics?.openDecisionCards)}<small>بطاقات مفتوحة</small></div></div>
        {!manager ? <div className="permission-note">بطاقات القرار متاحة لأدوار الإدارة فقط. تستمر مؤشرات التشغيل حسب صلاحيات حسابك.</div>
          : cardsQuery.isLoading ? <div className="empty-state">جارٍ تحميل بطاقات القرار…</div>
          : cardsQuery.isError ? <div className="alert alert-error">تعذر تحميل بطاقات القرار. تحقق من صلاحيات الإدارة.</div>
          : cards.length === 0 ? <div className="empty-state"><span>✓</span><strong>لا توجد بطاقات مفتوحة حالياً</strong><small>قد تظهر البطاقات بعد تحديث اللقطة التشغيلية عند تحقق إحدى قواعد التنبيه.</small></div>
          : <div className="decision-list">{cards.map((card) => <DecisionCardItem key={card.id} card={card} busy={statusMutation.isPending} onStatus={(status) => statusMutation.mutate({ id: card.id, status })} />)}</div>}
        {statusMutation.isError && <div className="alert alert-error">تعذر تحديث حالة بطاقة القرار.</div>}
      </section>
      <div className="dashboard-footnote"><span>ⓘ</span> المؤشرات وصفية ومجمّعة على مستوى الفرع. لا تعرض بيانات تعريف المرضى، ولا تتخذ قرارات طبية آلية.</div>
    </div>
  );
}

function DecisionCardItem({ card, busy, onStatus }: { card: DecisionCard; busy: boolean; onStatus: (status: DecisionCardStatus) => void }) {
  const severityLabel = card.severity === 'CRITICAL' ? 'حرج' : card.severity === 'WARNING' ? 'تحذير' : 'معلومة';
  return (
    <article className="decision-card">
      <div className="decision-topline"><span className={`severity severity-${card.severity.toLowerCase()}`}>{severityLabel}</span><span className="decision-time">{timestamp(card.generatedAt)}</span></div>
      <h3>{card.title}</h3>
      <p>{card.problemStatement}</p>
      <div className="decision-evidence"><strong>الدليل:</strong> {card.analysisSummary}</div>
      <div className="decision-recommendation"><strong>الإجراء المقترح:</strong> {card.recommendation}</div>
      <div className="decision-footer"><span>درجة الثقة: {number(Number(card.confidencePercent), 0)}%</span><div className="decision-actions">
        {card.status === 'OPEN' && <button disabled={busy} onClick={() => onStatus('ACKNOWLEDGED')}>استلام</button>}
        {['OPEN', 'ACKNOWLEDGED'].includes(card.status) && <button disabled={busy} onClick={() => onStatus('IN_PROGRESS')}>قيد المعالجة</button>}
        {['ACKNOWLEDGED', 'IN_PROGRESS'].includes(card.status) && <button className="button-complete" disabled={busy} onClick={() => onStatus('COMPLETED')}>إكمال</button>}
        <button className="button-muted" disabled={busy} onClick={() => onStatus('DISMISSED')}>إغلاق</button>
      </div></div>
    </article>
  );
}

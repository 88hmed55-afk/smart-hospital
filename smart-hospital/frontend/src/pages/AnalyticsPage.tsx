import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { RefreshCw, TrendingUp } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { DecisionCard, DashboardMetrics } from '../types/api';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

const err=(e:unknown)=>e instanceof ApiError?e.message:e instanceof Error?e.message:'تعذر تحميل التحليلات.';
const num=(n:number)=>Number(n??0).toLocaleString('ar-YE',{maximumFractionDigits:1});
const currency=(n:number,c:string)=>`${Number(n??0).toLocaleString('ar-YE',{maximumFractionDigits:2})} ${c}`;
const severity:Record<string,string>={INFO:'معلومة',WARNING:'تحذير',CRITICAL:'حرج'};
const status:Record<string,string>={OPEN:'مفتوحة',ACKNOWLEDGED:'تم الإقرار',IN_PROGRESS:'قيد التنفيذ',COMPLETED:'مكتملة',DISMISSED:'مستبعدة'};

type KpiExportRow = { label: string; value: string };
const kpiExportColumns: ExportColumn<KpiExportRow>[] = [
  { header: 'المؤشر', value: (row) => row.label, width: 2 },
  { header: 'القيمة', value: (row) => row.value, width: 1.4, align: 'left' },
];
const cardExportColumns: ExportColumn<DecisionCard>[] = [
  { header: 'الرمز', value: (row) => row.cardCode, width: 0.9, align: 'left' },
  { header: 'العنوان', value: (row) => row.title, width: 2 },
  { header: 'الشدة', value: (row) => severity[row.severity] ?? row.severity, width: 0.8, align: 'center' },
  { header: 'الحالة', value: (row) => status[row.status] ?? row.status, width: 0.9, align: 'center' },
  { header: 'القيمة المرصودة', value: (row) => `${num(row.observedValue)} ${row.unit ?? ''}`.trim(), width: 1 },
  { header: 'الحد التشغيلي', value: (row) => `${num(row.thresholdValue)} ${row.unit ?? ''}`.trim(), width: 1 },
  { header: 'الثقة %', value: (row) => num(row.confidencePercent), width: 0.7, align: 'center' },
  { header: 'التوصية', value: (row) => row.recommendation, width: 2.4 },
  { header: 'تاريخ التوليد', value: (row) => new Intl.DateTimeFormat('ar-YE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(row.generatedAt)), width: 1.4 },
];
export default function AnalyticsPage(){
 const {token,user}=useAuth();const {branchId,branch}=useOutletContext<LayoutOutletContext>();const qc=useQueryClient();const canManageCards=user?.roleCodes.some(r=>['HOSPITAL_ADMIN','HOSPITAL_MANAGER'].includes(r))??false;const canRefresh=canManageCards;
 const dashboard=useQuery({queryKey:['analytics-dashboard',branchId],queryFn:()=>apiRequest<DashboardMetrics>(`/analytics/dashboard?branchId=${branchId}`,token!),enabled:Boolean(token&&branchId)});
 const cards=useQuery({queryKey:['decision-cards',branchId],queryFn:()=>apiRequest<DecisionCard[]>(`/analytics/decision-cards?branchId=${branchId}&openOnly=false&limit=50`,token!),enabled:Boolean(token&&branchId&&canManageCards)});
 const refresh=useMutation({mutationFn:()=>apiRequest<DashboardMetrics>(`/analytics/dashboard/refresh?branchId=${branchId}`,token!,{method:'POST'}),onSuccess:async()=>{await qc.invalidateQueries({queryKey:['analytics-dashboard',branchId]});await qc.invalidateQueries({queryKey:['decision-cards',branchId]});}});
 const update=useMutation({mutationFn:({id,next}:{id:string;next:string})=>apiRequest<DecisionCard>(`/analytics/decision-cards/${id}/status`,token!,{method:'PATCH',body:JSON.stringify({status:next})}),onSuccess:async()=>{await qc.invalidateQueries({queryKey:['decision-cards',branchId]});await qc.invalidateQueries({queryKey:['analytics-dashboard',branchId]});}});
 const m=dashboard.data;
 const kpiRows:KpiExportRow[]=m?[
  {label:'مواعيد اليوم',value:num(m.appointmentsToday)},
  {label:'المواعيد المؤكدة الحضور',value:num(m.checkedInAppointments)},
  {label:'الزيارات الطبية',value:num(m.visitsToday)},
  {label:'حالات التنويم النشطة',value:num(m.activeAdmissions)},
  {label:'طلبات مختبر معلقة',value:num(m.pendingLabItems)},
  {label:'عينات ضمن القياس',value:num(m.labTurnaroundSamples)},
  {label:'إشغال الأسرة',value:`${num(m.bedOccupancyPercent)}%`},
  {label:'الأسرة المشغولة',value:`${num(m.occupiedBeds)} من ${num(m.operationalBeds)}`},
  {label:'متوسط دورة المختبر (دقيقة)',value:num(m.averageLabTurnaroundMinutes)},
  {label:'سياسات المخزون منخفضة الرصيد',value:num(m.lowStockMedicationPolicies)},
  {label:'الإيراد المرحّل اليوم',value:currency(m.postedRevenueToday,m.currencyCode)},
  {label:'بطاقات القرار المفتوحة',value:num(m.openDecisionCards)},
 ]:[];
 return <div className="module-page">
 <PageHeader eyebrow="BUSINESS INTELLIGENCE & DECISION SUPPORT" title="التحليلات الذكية ودعم القرار" description="مؤشرات تشغيلية مجمعة وبطاقات قرار قابلة للتفسير والمراجعة." icon={TrendingUp} meta={[`${cards.data?.length??0} بطاقة قرار`]} actions={<>
  <ExportMenu fileName="مؤشرات-التشغيل" title="تقرير مؤشرات التشغيل" subtitle={`ملخص المؤشرات — ${branch?.name??'الفرع الحالي'}`} columns={kpiExportColumns} rows={kpiRows} label="تصدير المؤشرات" disabled={!m} />
  {canManageCards&&<ExportMenu fileName="بطاقات-القرار" title="تقرير بطاقات القرار التشغيلي" subtitle="البطاقات التشغيلية وحالاتها وتوصياتها" columns={cardExportColumns} rows={cards.data??[]} label="تصدير البطاقات" />}
  {canRefresh&&<button className="primary-button" disabled={refresh.isPending} onClick={()=>refresh.mutate()}><RefreshCw size={15} className={refresh.isPending?'spin':undefined}/>{refresh.isPending?'جارٍ تحديث اللقطة…':'تحديث مؤشرات التشغيل'}</button>}
 </>} />
 {dashboard.isError&&<div className="alert alert-error">{err(dashboard.error)}</div>}{refresh.isError&&<div className="alert alert-error">{err(refresh.error)}</div>}
 {m&&<><div className="analytics-section-heading"><h2>ملخص التشغيل اليومي</h2><span>حتى {new Intl.DateTimeFormat('ar-YE',{dateStyle:'medium',timeStyle:'short'}).format(new Date(m.periodEnd))}</span></div><div className="analytics-kpi-grid">
 <Metric label="مواعيد اليوم" value={num(m.appointmentsToday)} sub={`وصل منهم ${num(m.checkedInAppointments)}`} mark="▣"/>
 <Metric label="الزيارات الطبية" value={num(m.visitsToday)} sub="زيارات مسجلة اليوم" mark="✚"/>
 <Metric label="حالات التنويم النشطة" value={num(m.activeAdmissions)} sub="على مستوى الفرع" mark="▥"/>
 <Metric label="طلبات مختبر معلقة" value={num(m.pendingLabItems)} sub={`عينات ضمن القياس ${num(m.labTurnaroundSamples)}`} mark="⚗"/>
 <Metric label="إشغال الأسرة" value={`${num(m.bedOccupancyPercent)}%`} sub={`${num(m.occupiedBeds)} من ${num(m.operationalBeds)} سرير تشغيلي`} mark="▤"/>
 <Metric label="متوسط دورة المختبر" value={`${num(m.averageLabTurnaroundMinutes)} دقيقة`} sub="للعينات الداخلة في القياس" mark="◷"/>
 <Metric label="سياسات المخزون منخفضة الرصيد" value={num(m.lowStockMedicationPolicies)} sub="وفق حدود إعادة الطلب" mark="▧"/>
 <Metric label="الإيراد المرحّل اليوم" value={currency(m.postedRevenueToday,m.currencyCode)} sub={`بطاقات القرار المفتوحة ${num(m.openDecisionCards)}`} mark="◫"/>
 </div></>}
 {canManageCards&&<section className="panel decision-panel"><div className="panel-heading"><div><h2>بطاقات القرار التشغيلي</h2><p>اقتراحات مبنية على مؤشرات وقواعد تشغيلية، وليست قرارات طبية.</p></div><span className="info-chip">{cards.data?.length??0} بطاقة</span></div>{cards.isError&&<div className="alert alert-error">{err(cards.error)}</div>}{cards.isLoading?<div className="empty-state">جارٍ تحميل بطاقات القرار…</div>:cards.data?.length?<div className="decision-grid">{cards.data.map(card=><article className="decision-card" key={card.id}><div className="decision-card-meta"><span className={`decision-severity severity-${card.severity.toLowerCase()}`}>{severity[card.severity]??card.severity}</span><span className="decision-code">{card.cardCode}</span><span className="decision-status">{status[card.status]??card.status}</span></div><h3>{card.title}</h3><p className="decision-problem">{card.problemStatement}</p><div className="decision-evidence"><div><small>القيمة المرصودة</small><strong>{card.observedValue==null?'—':`${num(card.observedValue)} ${card.unit??''}`}</strong></div><div><small>الحد التشغيلي</small><strong>{card.thresholdValue==null?'—':`${num(card.thresholdValue)} ${card.unit??''}`}</strong></div><div><small>الثقة</small><strong>{card.confidencePercent==null?'غير محددة':`${num(card.confidencePercent)}%`}</strong></div></div><div className="decision-detail"><strong>التحليل</strong><p>{card.analysisSummary}</p></div><div className="decision-detail"><strong>التوصية</strong><p>{card.recommendation}</p></div><details className="decision-assumptions"><summary>الافتراضات والأثر المتوقع</summary><pre>{JSON.stringify({assumptions:parseJson(card.assumptionsJson),expectedImpact:parseJson(card.expectedImpactJson)},null,2)}</pre></details><div className="decision-actions">{card.status==='OPEN'&&<button onClick={()=>update.mutate({id:card.id,next:'ACKNOWLEDGED'})} disabled={update.isPending}>إقرار</button>}{['OPEN','ACKNOWLEDGED'].includes(card.status)&&<button onClick={()=>update.mutate({id:card.id,next:'IN_PROGRESS'})} disabled={update.isPending}>بدء المعالجة</button>}{['ACKNOWLEDGED','IN_PROGRESS'].includes(card.status)&&<button onClick={()=>update.mutate({id:card.id,next:'COMPLETED'})} disabled={update.isPending}>إكمال</button>}{['OPEN','ACKNOWLEDGED'].includes(card.status)&&<button className="subtle-action" onClick={()=>update.mutate({id:card.id,next:'DISMISSED'})} disabled={update.isPending}>استبعاد</button>}</div></article>)}</div>:<div className="empty-state"><strong>لا توجد بطاقات قرار حالياً</strong><small>تظهر البطاقات عند تحقق قواعد التنبيه التشغيلي على بيانات الفرع.</small></div>}{update.isError&&<div className="alert alert-error">{err(update.error)}</div>}</section>}
 <p className="analytics-caution">مصدر الأرقام: بيانات التشغيل المسجلة في قاعدة بيانات النظام. الفترات الزمنية وحدود التنبيه تتبع إعدادات المؤشرات. لا تُستخدم هذه التحليلات لتشخيص المرضى أو اتخاذ قرارات علاجية.</p>
 </div>;
}
function Metric({label,value,sub,mark}:{label:string;value:string;sub:string;mark:string}){return <article className="analytics-metric"><div className="metric-mark">{mark}</div><small>{label}</small><strong>{value}</strong><span>{sub}</span></article>;}
function parseJson(value:string){try{return JSON.parse(value);}catch{return value;}}

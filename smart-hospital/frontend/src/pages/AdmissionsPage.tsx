import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { BedDouble } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

interface Bed { id:string; wardId:string; wardName:string; roomNumber:string; bedNumber:string; bedType:string; operationalStatus:string; occupied:boolean; available:boolean; }
interface Admission { id:string; admissionNumber:string; patientMrn:string; patientName:string; admissionType:string; status:string; reason?:string|null; admittedAt:string; attendingDoctorName:string; currentBedId?:string|null; currentBedLabel?:string|null; }
const fmt=(v:string)=>new Intl.DateTimeFormat('ar-YE',{dateStyle:'medium',timeStyle:'short'}).format(new Date(v));
const err=(e:unknown)=>e instanceof ApiError?e.message:e instanceof Error?e.message:'تعذر تنفيذ العملية.';

const admissionExportColumns: ExportColumn<Admission>[] = [
  { header:'رقم التنويم', value:(r)=>r.admissionNumber, width:1.1 },
  { header:'المريض', value:(r)=>r.patientName, width:1.7 },
  { header:'رقم الملف', value:(r)=>r.patientMrn, width:1 },
  { header:'الطبيب المسؤول', value:(r)=>r.attendingDoctorName, width:1.5 },
  { header:'نوع التنويم', value:(r)=>r.admissionType, width:1 },
  { header:'السرير الحالي', value:(r)=>r.currentBedLabel||'غير مخصص', width:1.3 },
  { header:'تاريخ الدخول', value:(r)=>fmt(r.admittedAt), width:1.4 },
  { header:'الحالة', value:(r)=>r.status, width:0.8, align:'center' },
];
const bedExportColumns: ExportColumn<Bed>[] = [
  { header:'الجناح', value:(r)=>r.wardName, width:1.3 },
  { header:'الغرفة', value:(r)=>r.roomNumber, width:0.8, align:'center' },
  { header:'السرير', value:(r)=>r.bedNumber, width:0.8, align:'center' },
  { header:'النوع', value:(r)=>r.bedType, width:1 },
  { header:'الحالة', value:(r)=>r.available?'متاح':r.occupied?'مشغول':'غير متاح', width:0.9, align:'center' },
];
export default function AdmissionsPage(){
 const {token,user}=useAuth();const {branchId,branch}=useOutletContext<LayoutOutletContext>();const qc=useQueryClient();
 const roles=user?.roleCodes??[];const canList=roles.some(r=>['HOSPITAL_ADMIN','HOSPITAL_MANAGER','DOCTOR','NURSE'].includes(r));const canDischarge=roles.some(r=>['HOSPITAL_ADMIN','HOSPITAL_MANAGER','DOCTOR'].includes(r));
 const [availableOnly,setAvailableOnly]=useState(false);const [selectedAdmission,setSelectedAdmission]=useState<Admission|null>(null);const [selectedBed,setSelectedBed]=useState('');const [reason,setReason]=useState('');const [disposition,setDisposition]=useState('HOME');const [summary,setSummary]=useState('');const [transferMode,setTransferMode]=useState(false);
 const beds=useQuery({queryKey:['admission-beds',branchId,availableOnly],queryFn:()=>apiRequest<Bed[]>(`/admissions/beds?branchId=${branchId}&availableOnly=${availableOnly}`,token!),enabled:Boolean(token&&branchId)});
 const admissions=useQuery({queryKey:['active-admissions',branchId],queryFn:()=>apiRequest<Admission[]>(`/admissions/active?branchId=${branchId}&limit=100`,token!),enabled:Boolean(token&&branchId&&canList)});
 const refresh=async()=>{await qc.invalidateQueries({queryKey:['admission-beds',branchId]});await qc.invalidateQueries({queryKey:['active-admissions',branchId]});};
 const bedAction=useMutation({mutationFn:()=>apiRequest<Admission>(`/admissions/${selectedAdmission!.id}/${transferMode?'bed-transfers':'bed-assignments'}`,token!,{method:'POST',body:JSON.stringify({bedId:selectedBed,reason:reason||undefined})}),onSuccess:async()=>{setSelectedAdmission(null);setSelectedBed('');setReason('');setTransferMode(false);await refresh();}});
 const discharge=useMutation({mutationFn:()=>apiRequest<Admission>(`/admissions/${selectedAdmission!.id}/discharge`,token!,{method:'POST',body:JSON.stringify({disposition,summary})}),onSuccess:async()=>{setSelectedAdmission(null);setSummary('');await refresh();}});
 return <div className="module-page">
 <PageHeader eyebrow="INPATIENT & BED MANAGEMENT" title="التنويم وإدارة الأسرة" description="متابعة المرضى المنومين، تخصيص الأسرة، وتسجيل الخروج." icon={BedDouble} meta={[`${admissions.data?.length??0} حالة نشطة`,`${beds.data?.filter(b=>b.available).length??0} سرير متاح`]} actions={<>
  <ExportMenu fileName="حالات-التنويم" title="تقرير حالات التنويم النشطة" subtitle={`المرضى المنومون — ${branch?.name??'الفرع الحالي'}`} columns={admissionExportColumns} rows={admissions.data??[]} label="تصدير التنويم" />
  <ExportMenu fileName="خريطة-الأسرة" title="تقرير خريطة الأسرة" subtitle={`حالة الأسرة — ${branch?.name??'الفرع الحالي'}`} columns={bedExportColumns} rows={beds.data??[]} label="تصدير الأسرة" />
 </>} />
 <section className="panel"><div className="panel-heading"><div><h2>حالة الأسرة</h2><p>يحسب الخادم الإشغال من تخصيصات الأسرة الفعالة.</p></div><label className="checkbox-field"><input type="checkbox" checked={availableOnly} onChange={e=>setAvailableOnly(e.target.checked)}/> المتاحة فقط</label></div>{beds.isError&&<div className="alert alert-error">{err(beds.error)}</div>}{beds.isLoading?<div className="empty-state">جارٍ تحميل الأسرة…</div>:beds.data?.length?<div className="bed-grid">{beds.data.map(b=><div key={b.id} className={`bed-tile ${b.available?'bed-available':'bed-occupied'}`}><div><strong>{b.bedNumber}</strong><span>{b.available?'متاح':b.occupied?'مشغول':'غير متاح'}</span></div><small>{b.wardName} · غرفة {b.roomNumber} · {b.bedType}</small></div>)}</div>:<div className="empty-state">لا توجد أسرة متاحة للعرض في هذا الفرع.</div>}</section>
 {canList&&<section className="panel admission-panel"><div className="panel-heading"><div><h2>حالات التنويم النشطة</h2><p>قائمة المرضى المنومين ضمن نطاق صلاحياتك.</p></div><span className="info-chip">{admissions.data?.length??0} حالة نشطة</span></div>{admissions.isError&&<div className="alert alert-error">{err(admissions.error)}</div>}{admissions.isLoading?<div className="empty-state">جارٍ تحميل حالات التنويم…</div>:admissions.data?.length?<div className="table-wrap"><table className="data-table"><thead><tr><th>رقم التنويم</th><th>المريض</th><th>الطبيب المسؤول</th><th>نوع التنويم</th><th>السرير الحالي</th><th>تاريخ الدخول</th><th>الإجراءات</th></tr></thead><tbody>{admissions.data.map(a=><tr key={a.id}><td>{a.admissionNumber}</td><td><div className="stacked-cell"><strong>{a.patientName}</strong><small>{a.patientMrn}</small></div></td><td>{a.attendingDoctorName}</td><td>{a.admissionType}</td><td>{a.currentBedLabel||'غير مخصص'}</td><td>{fmt(a.admittedAt)}</td><td><div className="row-actions"><button className="table-action" onClick={()=>{setSelectedAdmission(a);setTransferMode(Boolean(a.currentBedId));setSelectedBed('');setReason('');}}> {a.currentBedId?'نقل سرير':'تخصيص سرير'}</button>{canDischarge&&<button className="table-action danger-text" onClick={()=>{setSelectedAdmission(a);setSummary('');}}>خروج</button>}</div></td></tr>)}</tbody></table></div>:<div className="empty-state">لا توجد حالات تنويم نشطة في هذا الفرع.</div>}</section>}
 {(bedAction.isError||discharge.isError)&&<div className="alert alert-error">{err(bedAction.error||discharge.error)}</div>}
 {selectedAdmission&&<div className="modal-backdrop"><div className="modal-card"><div className="modal-header"><div><h2>{summary!==''||canDischarge?'إجراءات المريض المنوم':'تخصيص سرير'}</h2><p>{selectedAdmission.patientName} · {selectedAdmission.admissionNumber}</p></div><button className="icon-button" onClick={()=>setSelectedAdmission(null)}>×</button></div>
 <form className="form-grid" onSubmit={e=>{e.preventDefault();if(canDischarge&&summary.trim())discharge.mutate();else bedAction.mutate();}}>
 <label className="full-field">الإجراء<select value={transferMode?'TRANSFER':'ASSIGN'} onChange={e=>setTransferMode(e.target.value==='TRANSFER')}><option value="ASSIGN">تخصيص سرير</option><option value="TRANSFER" disabled={!selectedAdmission.currentBedId}>نقل إلى سرير آخر</option></select></label>
 <label className="full-field">السرير المستهدف<select required value={selectedBed} onChange={e=>setSelectedBed(e.target.value)}><option value="">اختر سريراً متاحاً</option>{beds.data?.filter(b=>b.available&&b.id!==selectedAdmission.currentBedId).map(b=><option key={b.id} value={b.id}>{b.wardName} · غرفة {b.roomNumber} · سرير {b.bedNumber}</option>)}</select></label>
 <label className="full-field">سبب التخصيص أو النقل<textarea rows={2} value={reason} onChange={e=>setReason(e.target.value)}/></label>
 {canDischarge&&<><div className="form-separator full-field">تسجيل الخروج</div><label>حالة الخروج<select value={disposition} onChange={e=>setDisposition(e.target.value)}><option value="HOME">إلى المنزل</option><option value="TRANSFERRED">تحويل</option><option value="AMA">خروج بناءً على رغبة المريض</option><option value="DECEASED">وفاة</option><option value="OTHER">أخرى</option></select></label><label className="full-field">ملخص الخروج<textarea rows={3} value={summary} onChange={e=>setSummary(e.target.value)}/></label><p className="form-hint full-field">لتسجيل الخروج اضغط زر «تسجيل الخروج» أدناه. عملية السرير مستقلة عن عملية الخروج.</p></>}
 <div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={()=>setSelectedAdmission(null)}>إغلاق</button><button type="submit" className="primary-button" disabled={bedAction.isPending||discharge.isPending||!selectedBed}>{transferMode?'تأكيد النقل':'تخصيص السرير'}</button>{canDischarge&&<button type="button" className="danger-outline-button" disabled={discharge.isPending||!summary.trim()} onClick={()=>discharge.mutate()}>تسجيل الخروج</button>}</div>
 </form></div></div>}
 </div>;
}

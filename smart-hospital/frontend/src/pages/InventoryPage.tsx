import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { Boxes, PackagePlus, Truck } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

interface Supplier { id: string; supplierCode: string; supplierName: string; contactPerson?: string | null; phoneNumber?: string | null; email?: string | null; status: string; }
interface Medication { id: string; code: string; genericName: string; brandName?: string | null; strength?: string | null; dispensingUnit: string; }
interface InventoryLocation { id: string; code: string; name: string; }
interface PurchaseOrderItem { id: string; medicationId: string; medicationCode: string; medicationName: string; description: string; lineNumber: number; orderedQuantity: number; receivedQuantity: number; remainingQuantity: number; unitOfMeasure: string; unitCost: number; lineDiscount: number; lineTotal: number; }
interface PurchaseOrder { id: string; branchId: string; supplierId: string; supplierName: string; orderNumber: string; status: string; currencyCode: string; subtotal: number; discountAmount: number; taxAmount: number; totalAmount: number; requestedBy: string; approvedBy?: string | null; expectedDeliveryOn?: string | null; notes?: string | null; items: PurchaseOrderItem[]; }
interface SupplierBody { supplierCode: string; supplierName: string; contactPerson?: string; phoneNumber?: string; email?: string; address?: string; registrationNumber?: string; }
interface PurchaseOrderBody { branchId: string; supplierId: string; expectedDeliveryOn?: string; notes?: string; items: { medicationId: string; quantity: number; unitOfMeasure: string; unitCost: number; lineDiscount: number }[]; }
interface GoodsReceiptBody { branchId: string; inventoryLocationId: string; supplierDeliveryReference?: string; notes?: string; items: { purchaseOrderItemId: string; acceptedQuantity: number; rejectedQuantity: number; batchNumber?: string; manufacturer?: string; manufacturedOn?: string; expiresOn?: string; rejectionReason?: string }[]; }
type ReceiptLine = { purchaseOrderItemId: string; acceptedQuantity: string; rejectedQuantity: string; batchNumber: string; manufacturer: string; manufacturedOn: string; expiresOn: string; rejectionReason: string };
const tomorrowIso = () => { const d = new Date(); d.setDate(d.getDate() + 1); return d.toISOString().slice(0, 10); };

const statusLabels: Record<string, string> = {
  DRAFT: 'مسودة', SUBMITTED: 'بانتظار الموافقة', APPROVED: 'معتمد', ORDERED: 'أُرسل للمورد',
  PARTIALLY_RECEIVED: 'استلام جزئي', RECEIVED: 'مستلم بالكامل', REJECTED: 'مرفوض', CANCELLED: 'ملغي',
};
const statusClass: Record<string, string> = {
  DRAFT: 'status-booked', SUBMITTED: 'status-pending', APPROVED: 'status-active', ORDERED: 'status-active',
  PARTIALLY_RECEIVED: 'status-pending', RECEIVED: 'status-active', REJECTED: 'status-inactive', CANCELLED: 'status-inactive',
};
const errorText = (error: unknown) => error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'تعذر تنفيذ العملية.';
const formatMoney = (amount: number, currency: string) => `${new Intl.NumberFormat('ar-YE', { maximumFractionDigits: 2 }).format(amount)} ${currency}`;

export default function InventoryPage() {
  const { token, user } = useAuth();
  const { branchId, branch } = useOutletContext<LayoutOutletContext>();
  const queryClient = useQueryClient();
  const roles = user?.roleCodes ?? [];
  const canCreate = roles.some((r) => ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'INVENTORY_OFFICER'].includes(r));
  const canApprove = roles.some((r) => ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER'].includes(r));
  const [orderStatus, setOrderStatus] = useState('');
  const [expandedOrder, setExpandedOrder] = useState<string | null>(null);
  const [showSupplierModal, setShowSupplierModal] = useState(false);
  const [showOrderModal, setShowOrderModal] = useState(false);
  const [receivingOrder, setReceivingOrder] = useState<PurchaseOrder | null>(null);
  const [approvalNote, setApprovalNote] = useState('');
  const [actionError, setActionError] = useState('');

  const suppliersQuery = useQuery({
    queryKey: ['procurement-suppliers'],
    queryFn: () => apiRequest<Supplier[]>('/procurement/suppliers?limit=100', token!),
    enabled: Boolean(token && canCreate),
  });
  const locationsQuery = useQuery({
    queryKey: ['procurement-locations', branchId],
    queryFn: () => apiRequest<InventoryLocation[]>(`/procurement/locations?branchId=${branchId}`, token!),
    enabled: Boolean(token && branchId && canCreate),
  });
  const ordersQuery = useQuery({
    queryKey: ['purchase-orders', branchId, orderStatus],
    queryFn: () => apiRequest<PurchaseOrder[]>(`/procurement/purchase-orders?branchId=${branchId}&limit=100${orderStatus ? `&status=${orderStatus}` : ''}`, token!),
    enabled: Boolean(token && branchId && canCreate),
  });

  const supplierMutation = useMutation({
    mutationFn: (body: SupplierBody) => apiRequest<Supplier>('/procurement/suppliers', token!, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: async () => { setShowSupplierModal(false); await queryClient.invalidateQueries({ queryKey: ['procurement-suppliers'] }); },
  });
  const orderMutation = useMutation({
    mutationFn: (body: PurchaseOrderBody) => apiRequest<PurchaseOrder>('/procurement/purchase-orders', token!, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: async () => { setShowOrderModal(false); await queryClient.invalidateQueries({ queryKey: ['purchase-orders'] }); },
  });
  const submitMutation = useMutation({
    mutationFn: (id: string) => apiRequest<PurchaseOrder>(`/procurement/purchase-orders/${id}/submit`, token!, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['purchase-orders'] }),
  });
  const approvalMutation = useMutation({
    mutationFn: ({ id, approved }: { id: string; approved: boolean }) => apiRequest<PurchaseOrder>(`/procurement/purchase-orders/${id}/approval`, token!, { method: 'POST', body: JSON.stringify({ approved, note: approvalNote || undefined }) }),
    onSuccess: async () => { setApprovalNote(''); await queryClient.invalidateQueries({ queryKey: ['purchase-orders'] }); },
  });
  const orderToSupplierMutation = useMutation({
    mutationFn: (id: string) => apiRequest<PurchaseOrder>(`/procurement/purchase-orders/${id}/order`, token!, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['purchase-orders'] }),
  });
  const receiptMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: GoodsReceiptBody }) => apiRequest(`/procurement/purchase-orders/${id}/receipts`, token!, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: async () => { setReceivingOrder(null); await queryClient.invalidateQueries({ queryKey: ['purchase-orders'] }); await queryClient.invalidateQueries({ queryKey: ['pharmacy-stock'] }); },
  });

  const orders = ordersQuery.data ?? [];
  const totals = useMemo(() => ({
    active: orders.filter((o) => ['SUBMITTED', 'APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED'].includes(o.status)).length,
    awaiting: orders.filter((o) => o.status === 'SUBMITTED').length,
    receiving: orders.filter((o) => ['ORDERED', 'PARTIALLY_RECEIVED'].includes(o.status)).length,
  }), [orders]);
  const anyMutationError = supplierMutation.error || orderMutation.error || submitMutation.error || approvalMutation.error || orderToSupplierMutation.error || receiptMutation.error;

  const refresh = () => { void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] }); void queryClient.invalidateQueries({ queryKey: ['procurement-suppliers'] }); };

  const orderExportColumns: ExportColumn<PurchaseOrder>[] = [
    { header: 'رقم الطلب', value: (row) => row.orderNumber, width: 1.1 },
    { header: 'المورد', value: (row) => row.supplierName, width: 1.6 },
    { header: 'الحالة', value: (row) => statusLabels[row.status] ?? row.status, width: 1 },
    { header: 'العملة', value: (row) => row.currencyCode, width: 0.6, align: 'center' },
    { header: 'الإجمالي', value: (row) => Number(row.totalAmount), width: 1.1 },
    { header: 'تاريخ التسليم المتوقع', value: (row) => row.expectedDeliveryOn ?? '—', width: 1.2, align: 'center' },
    { header: 'أنشأه', value: (row) => row.requestedBy, width: 1.1 },
    { header: 'اعتمده', value: (row) => row.approvedBy ?? '—', width: 1.1 },
  ];
  const supplierExportColumns: ExportColumn<Supplier>[] = [
    { header: 'رمز المورد', value: (row) => row.supplierCode, width: 1 },
    { header: 'اسم المورد', value: (row) => row.supplierName, width: 1.8 },
    { header: 'جهة الاتصال', value: (row) => row.contactPerson ?? '—', width: 1.2 },
    { header: 'الهاتف', value: (row) => row.phoneNumber ?? '—', width: 1.1, align: 'left' },
    { header: 'البريد', value: (row) => row.email ?? '—', width: 1.5, align: 'left' },
    { header: 'الحالة', value: (row) => row.status, width: 0.8, align: 'center' },
  ];

  return (
    <div className="module-page">
      <PageHeader
        eyebrow="INVENTORY & PROCUREMENT"
        title="المخزون والمشتريات"
        description="دورة شراء موثقة من المورد إلى استلام الدفعة وترحيل الكمية للمخزون."
        icon={Boxes}
        meta={[`${totals.active} طلب نشط`, `${totals.awaiting} بانتظار الموافقة`]}
        actions={
          <>
            <ExportMenu fileName="أوامر-الشراء" title="تقرير أوامر الشراء" subtitle={`أوامر الشراء — ${branch?.name ?? 'الفرع الحالي'}`} meta={[{ label: 'الحالة', value: orderStatus ? statusLabels[orderStatus] ?? orderStatus : 'كل الحالات' }]} columns={orderExportColumns} rows={orders} label="تصدير الطلبات" />
            {canCreate && <ExportMenu fileName="الموردون" title="تقرير الموردين" subtitle="سجل الموردين المعتمدين" columns={supplierExportColumns} rows={suppliersQuery.data ?? []} label="تصدير الموردين" />}
            {canCreate && (
              <>
                <button className="secondary-button" onClick={() => setShowSupplierModal(true)}><Truck size={16} /> مورد جديد</button>
                <button className="primary-button" onClick={() => setShowOrderModal(true)}><PackagePlus size={16} /> طلب شراء</button>
              </>
            )}
          </>
        }
      />

      <section className="stat-grid stat-grid-3">
        <article className="stat-card"><span>طلبات نشطة</span><strong>{totals.active.toLocaleString('ar-YE')}</strong><small>قيد المعالجة أو الاستلام</small></article>
        <article className="stat-card"><span>بانتظار الموافقة</span><strong>{totals.awaiting.toLocaleString('ar-YE')}</strong><small>تحتاج مراجعة إدارية</small></article>
        <article className="stat-card"><span>طلبات بانتظار الاستلام</span><strong>{totals.receiving.toLocaleString('ar-YE')}</strong><small>أُرسلت للمورد أو استُلم جزء منها</small></article>
      </section>

      <section className="panel">
        <div className="panel-heading"><div><h2>أوامر الشراء</h2><p>كل طلب يمر بالإنشاء ثم الاعتماد ثم الإرسال والاستلام.</p></div><div className="panel-tools"><select className="compact-select" value={orderStatus} onChange={(e) => setOrderStatus(e.target.value)}><option value="">كل الحالات</option>{Object.entries(statusLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select><button className="icon-button" onClick={refresh} title="تحديث">↻</button></div></div>
        {anyMutationError && <div className="alert alert-error">{errorText(anyMutationError)}</div>}
        {actionError && <div className="alert alert-error">{actionError}</div>}
        {ordersQuery.isError && <div className="alert alert-error">{errorText(ordersQuery.error)}</div>}
        {ordersQuery.isLoading ? <div className="empty-state">جارٍ تحميل أوامر الشراء…</div> : orders.length ? (
          <div className="procurement-list">
            {orders.map((order) => (
              <article className="purchase-order-card" key={order.id}>
                <div className="purchase-order-main">
                  <div className="purchase-order-ident"><strong>{order.orderNumber}</strong><span>{order.supplierName}</span><small>{order.expectedDeliveryOn ? `التسليم المتوقع: ${order.expectedDeliveryOn}` : 'لا يوجد تاريخ تسليم محدد'}</small></div>
                  <div className="purchase-order-amount"><strong>{formatMoney(order.totalAmount, order.currencyCode)}</strong><span className={`appointment-status ${statusClass[order.status] ?? ''}`}>{statusLabels[order.status] ?? order.status}</span></div>
                  <button className="table-action" onClick={() => setExpandedOrder(expandedOrder === order.id ? null : order.id)}>{expandedOrder === order.id ? 'إخفاء التفاصيل ↑' : 'تفاصيل الطلب ↓'}</button>
                </div>
                {expandedOrder === order.id && <>
                  <div className="table-wrap"><table className="data-table"><thead><tr><th>الصنف</th><th>الكمية المطلوبة</th><th>المستلمة</th><th>المتبقية</th><th>سعر الوحدة</th><th>الإجمالي</th></tr></thead><tbody>{order.items.map((item) => <tr key={item.id}><td><strong>{item.medicationName}</strong><small className="cell-subtitle">{item.medicationCode}</small></td><td>{item.orderedQuantity} {item.unitOfMeasure}</td><td>{item.receivedQuantity}</td><td>{item.remainingQuantity}</td><td>{formatMoney(item.unitCost, order.currencyCode)}</td><td>{formatMoney(item.lineTotal, order.currencyCode)}</td></tr>)}</tbody></table></div>
                  <div className="purchase-order-footer"><span>المجموع {formatMoney(order.subtotal, order.currencyCode)} · الخصم {formatMoney(order.discountAmount, order.currencyCode)}</span><div className="inline-actions">
                    {order.status === 'DRAFT' && <button className="secondary-button small" disabled={submitMutation.isPending} onClick={() => { setActionError(''); submitMutation.mutate(order.id); }}>إرسال للموافقة</button>}
                    {order.status === 'SUBMITTED' && canApprove && <><input className="compact-input" placeholder="ملاحظة الاعتماد (اختياري)" value={approvalNote} onChange={(e) => setApprovalNote(e.target.value)} /><button className="secondary-button small" disabled={approvalMutation.isPending} onClick={() => approvalMutation.mutate({ id: order.id, approved: false })}>رفض</button><button className="primary-button small" disabled={approvalMutation.isPending} onClick={() => approvalMutation.mutate({ id: order.id, approved: true })}>اعتماد</button></>}
                    {order.status === 'APPROVED' && <button className="primary-button small" disabled={orderToSupplierMutation.isPending} onClick={() => orderToSupplierMutation.mutate(order.id)}>تأكيد الإرسال للمورد</button>}
                    {['ORDERED', 'PARTIALLY_RECEIVED'].includes(order.status) && <button className="primary-button small" onClick={() => setReceivingOrder(order)}>＋ تسجيل استلام</button>}
                  </div></div>
                </>}
              </article>
            ))}
          </div>
        ) : <div className="empty-state"><strong>لا توجد أوامر شراء</strong><small>أنشئ أول طلب شراء لإدارة التوريد والاستلام من هذه الصفحة.</small></div>}
      </section>

      <section className="panel supplier-panel"><div className="panel-heading"><div><h2>الموردون</h2><p>دليل الموردين المعتمدين للتوريد.</p></div><span className="result-count">{suppliersQuery.data?.length ?? '—'} مورد</span></div>{suppliersQuery.isError && <div className="alert alert-error">{errorText(suppliersQuery.error)}</div>}{suppliersQuery.isLoading ? <div className="empty-mini">جارٍ تحميل الموردين…</div> : suppliersQuery.data?.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>الرمز</th><th>اسم المورد</th><th>مسؤول التواصل</th><th>الهاتف</th><th>البريد</th><th>الحالة</th></tr></thead><tbody>{suppliersQuery.data.map((s) => <tr key={s.id}><td><strong>{s.supplierCode}</strong></td><td>{s.supplierName}</td><td>{s.contactPerson || '—'}</td><td dir="ltr" className="ltr-cell">{s.phoneNumber || '—'}</td><td dir="ltr" className="ltr-cell">{s.email || '—'}</td><td>{s.status === 'ACTIVE' ? 'نشط' : s.status}</td></tr>)}</tbody></table></div> : <div className="empty-mini">لا يوجد موردون مسجلون بعد.</div>}</section>

      {showSupplierModal && <SupplierModal busy={supplierMutation.isPending} error={supplierMutation.error} onClose={() => { setShowSupplierModal(false); supplierMutation.reset(); }} onSubmit={(body) => supplierMutation.mutate(body)} />}
      {showOrderModal && <PurchaseOrderModal token={token!} branchId={branchId} suppliers={suppliersQuery.data ?? []} suppliersLoading={suppliersQuery.isLoading} busy={orderMutation.isPending} error={orderMutation.error} onClose={() => { setShowOrderModal(false); orderMutation.reset(); }} onSubmit={(body) => orderMutation.mutate(body)} />}
      {receivingOrder && <GoodsReceiptModal order={receivingOrder} locations={locationsQuery.data ?? []} busy={receiptMutation.isPending} error={receiptMutation.error} onClose={() => { setReceivingOrder(null); receiptMutation.reset(); }} onSubmit={(body) => receiptMutation.mutate({ id: receivingOrder.id, body })} />}
    </div>
  );
}

function SupplierModal({ busy, error, onClose, onSubmit }: { busy: boolean; error: unknown; onClose: () => void; onSubmit: (body: SupplierBody) => void }) {
  const [form, setForm] = useState({ supplierCode: '', supplierName: '', contactPerson: '', phoneNumber: '', email: '', address: '', registrationNumber: '' });
  function submit(event: FormEvent) { event.preventDefault(); onSubmit({ supplierCode: form.supplierCode, supplierName: form.supplierName, contactPerson: form.contactPerson || undefined, phoneNumber: form.phoneNumber || undefined, email: form.email || undefined, address: form.address || undefined, registrationNumber: form.registrationNumber || undefined }); }
  return <div className="modal-backdrop"><section className="modal-card"><div className="modal-header"><div><h2>إضافة مورد</h2><p>بيانات المورد الأساسية.</p></div><button className="icon-button" onClick={onClose}>×</button></div><form className="form-grid" onSubmit={submit}><label>رمز المورد<input required maxLength={32} value={form.supplierCode} onChange={(e) => setForm({ ...form, supplierCode: e.target.value })} /></label><label>اسم المورد<input required maxLength={180} value={form.supplierName} onChange={(e) => setForm({ ...form, supplierName: e.target.value })} /></label><label>مسؤول التواصل<input maxLength={140} value={form.contactPerson} onChange={(e) => setForm({ ...form, contactPerson: e.target.value })} /></label><label>الهاتف<input maxLength={32} value={form.phoneNumber} onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} /></label><label>البريد الإلكتروني<input type="email" maxLength={254} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label><label>رقم التسجيل<input maxLength={80} value={form.registrationNumber} onChange={(e) => setForm({ ...form, registrationNumber: e.target.value })} /></label><label className="full-field">العنوان<input maxLength={500} value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></label>{error ? <div className="alert alert-error full-field">{errorText(error)}</div> : null}<div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={onClose}>إلغاء</button><button className="primary-button" disabled={busy}>{busy ? 'جارٍ الحفظ…' : 'حفظ المورد'}</button></div></form></section></div>;
}

function PurchaseOrderModal({ token, branchId, suppliers, suppliersLoading, busy, error, onClose, onSubmit }: { token: string; branchId: string; suppliers: Supplier[]; suppliersLoading: boolean; busy: boolean; error: unknown; onClose: () => void; onSubmit: (body: PurchaseOrderBody) => void }) {
  const [supplierId, setSupplierId] = useState('');
  const [expectedDeliveryOn, setExpectedDeliveryOn] = useState('');
  const [notes, setNotes] = useState('');
  const [medicationTerm, setMedicationTerm] = useState('');
  const [lines, setLines] = useState<{ medication: Medication; quantity: string; unitCost: string; discount: string }[]>([]);
  const medsQuery = useQuery({ queryKey: ['procurement-medications', medicationTerm], queryFn: () => apiRequest<Medication[]>(`/medications?q=${encodeURIComponent(medicationTerm)}&limit=20`, token), enabled: medicationTerm.trim().length >= 2 });
  function addMedication(medication: Medication) { if (!lines.some((line) => line.medication.id === medication.id)) setLines([...lines, { medication, quantity: '1', unitCost: '0.00', discount: '0.00' }]); }
  function submit(event: FormEvent) { event.preventDefault(); onSubmit({ branchId, supplierId, expectedDeliveryOn: expectedDeliveryOn || undefined, notes: notes || undefined, items: lines.map((line) => ({ medicationId: line.medication.id, quantity: Number(line.quantity), unitOfMeasure: line.medication.dispensingUnit, unitCost: Number(line.unitCost), lineDiscount: Number(line.discount) })) }); }
  const valid = Boolean(supplierId && lines.length && lines.every((line) => Number(line.quantity) > 0 && Number(line.unitCost) >= 0 && Number(line.discount) >= 0 && Number(line.discount) <= Number(line.quantity) * Number(line.unitCost))); 
  return <div className="modal-backdrop"><section className="modal-card modal-wide"><div className="modal-header"><div><h2>إنشاء طلب شراء</h2><p>يُنشأ كمسودة ثم يُرسل للموافقة قبل إرساله إلى المورد.</p></div><button className="icon-button" onClick={onClose}>×</button></div><form className="form-grid" onSubmit={submit}><label>المورد<select required value={supplierId} onChange={(e) => setSupplierId(e.target.value)}><option value="">{suppliersLoading ? 'جارٍ تحميل الموردين…' : 'اختر المورد'}</option>{suppliers.filter((s) => s.status === 'ACTIVE').map((s) => <option key={s.id} value={s.id}>{s.supplierName} ({s.supplierCode})</option>)}</select></label><label>تاريخ التسليم المتوقع<input type="date" value={expectedDeliveryOn} onChange={(e) => setExpectedDeliveryOn(e.target.value)} /></label><label className="full-field">ملاحظات<input value={notes} maxLength={1000} onChange={(e) => setNotes(e.target.value)} /></label><div className="full-field medication-picker"><label>إضافة دواء إلى الطلب<input value={medicationTerm} onChange={(e) => setMedicationTerm(e.target.value)} placeholder="ابحث باسم الدواء أو رمزه (حرفان على الأقل)" /></label>{medsQuery.data?.length ? <div className="picker-results">{medsQuery.data.map((m) => <button type="button" key={m.id} onClick={() => addMedication(m)} disabled={lines.some((line) => line.medication.id === m.id)}><strong>{m.genericName} {m.strength || ''}</strong><small>{m.code} · {m.dispensingUnit}</small><span>＋</span></button>)}</div> : medicationTerm.length >= 2 && !medsQuery.isLoading ? <div className="empty-mini">لا توجد أدوية مطابقة.</div> : null}</div><div className="full-field"><h3 className="form-section-title">بنود الطلب ({lines.length})</h3>{lines.length ? <div className="po-edit-lines">{lines.map((line, index) => <div className="po-edit-line" key={line.medication.id}><div><strong>{line.medication.genericName} {line.medication.strength || ''}</strong><small>{line.medication.code}</small></div><label>الكمية<input type="number" min="0.001" step="0.001" value={line.quantity} onChange={(e) => setLines(lines.map((x, i) => i === index ? { ...x, quantity: e.target.value } : x))} /></label><label>سعر الوحدة<input type="number" min="0" step="0.01" value={line.unitCost} onChange={(e) => setLines(lines.map((x, i) => i === index ? { ...x, unitCost: e.target.value } : x))} /></label><label>الخصم<input type="number" min="0" step="0.01" value={line.discount} onChange={(e) => setLines(lines.map((x, i) => i === index ? { ...x, discount: e.target.value } : x))} /></label><button type="button" className="icon-button danger-icon" onClick={() => setLines(lines.filter((_, i) => i !== index))}>×</button></div>)}</div> : <div className="empty-mini">ابحث عن الأدوية وأضفها إلى طلب الشراء.</div>}</div>{error ? <div className="alert alert-error full-field">{errorText(error)}</div> : null}<div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={onClose}>إلغاء</button><button className="primary-button" disabled={busy || !valid}>{busy ? 'جارٍ إنشاء الطلب…' : 'حفظ كمسودة'}</button></div></form></section></div>;
}

function GoodsReceiptModal({ order, locations, busy, error, onClose, onSubmit }: { order: PurchaseOrder; locations: InventoryLocation[]; busy: boolean; error: unknown; onClose: () => void; onSubmit: (body: GoodsReceiptBody) => void }) {
  const [locationId, setLocationId] = useState('');
  const [supplierDeliveryReference, setSupplierDeliveryReference] = useState('');
  const [notes, setNotes] = useState('');
  const pendingItems = order.items.filter((item) => item.remainingQuantity > 0);
  const [lines, setLines] = useState<ReceiptLine[]>(() => pendingItems.map((item) => ({ purchaseOrderItemId: item.id, acceptedQuantity: '0', rejectedQuantity: '0', batchNumber: '', manufacturer: '', manufacturedOn: '', expiresOn: '', rejectionReason: '' })));
  function updateLine(id: string, key: keyof ReceiptLine, value: string) { setLines(lines.map((line) => line.purchaseOrderItemId === id ? { ...line, [key]: value } : line)); }
  function submit(event: FormEvent) { event.preventDefault(); onSubmit({ branchId: order.branchId, inventoryLocationId: locationId, supplierDeliveryReference: supplierDeliveryReference || undefined, notes: notes || undefined, items: lines.filter((line) => Number(line.acceptedQuantity) > 0 || Number(line.rejectedQuantity) > 0).map((line) => ({ purchaseOrderItemId: line.purchaseOrderItemId, acceptedQuantity: Number(line.acceptedQuantity), rejectedQuantity: Number(line.rejectedQuantity), batchNumber: line.batchNumber || undefined, manufacturer: line.manufacturer || undefined, manufacturedOn: line.manufacturedOn || undefined, expiresOn: line.expiresOn || undefined, rejectionReason: line.rejectionReason || undefined })) }); }
  const valid = Boolean(locationId && lines.some((line) => Number(line.acceptedQuantity) > 0 || Number(line.rejectedQuantity) > 0) && lines.every((line) => Number(line.acceptedQuantity) >= 0 && Number(line.rejectedQuantity) >= 0 && Number(line.acceptedQuantity) + Number(line.rejectedQuantity) <= (pendingItems.find((item) => item.id === line.purchaseOrderItemId)?.remainingQuantity ?? 0) && (Number(line.acceptedQuantity) === 0 || (line.batchNumber.trim() && line.expiresOn)) && (Number(line.rejectedQuantity) === 0 || line.rejectionReason.trim())));
  return <div className="modal-backdrop"><section className="modal-card modal-wide"><div className="modal-header"><div><h2>تسجيل استلام بضاعة</h2><p>{order.orderNumber} · {order.supplierName}</p></div><button className="icon-button" onClick={onClose}>×</button></div><form className="form-grid" onSubmit={submit}><label>موقع التخزين<select required value={locationId} onChange={(e) => setLocationId(e.target.value)}><option value="">اختر المستودع أو الصيدلية</option>{locations.map((l) => <option key={l.id} value={l.id}>{l.name} ({l.code})</option>)}</select></label><label>مرجع تسليم المورد<input value={supplierDeliveryReference} maxLength={100} onChange={(e) => setSupplierDeliveryReference(e.target.value)} /></label><label className="full-field">ملاحظات الاستلام<input value={notes} maxLength={1000} onChange={(e) => setNotes(e.target.value)} /></label><div className="full-field"><h3 className="form-section-title">الكميات المستلمة لكل بند</h3><div className="receipt-lines">{pendingItems.map((item) => { const line = lines.find((x) => x.purchaseOrderItemId === item.id)!; return <article className="receipt-line-card" key={item.id}><div className="receipt-line-heading"><strong>{item.medicationName}</strong><span>متبقٍ {item.remainingQuantity} {item.unitOfMeasure}</span></div><div className="form-grid compact-grid"><label>كمية مقبولة<input type="number" min="0" max={item.remainingQuantity} step="0.001" value={line.acceptedQuantity} onChange={(e) => updateLine(item.id, 'acceptedQuantity', e.target.value)} /></label><label>كمية مرفوضة<input type="number" min="0" max={item.remainingQuantity} step="0.001" value={line.rejectedQuantity} onChange={(e) => updateLine(item.id, 'rejectedQuantity', e.target.value)} /></label>{Number(line.acceptedQuantity) > 0 && <><label>رقم التشغيلة<input required value={line.batchNumber} maxLength={80} onChange={(e) => updateLine(item.id, 'batchNumber', e.target.value)} /></label><label>الشركة المصنعة<input value={line.manufacturer} maxLength={160} onChange={(e) => updateLine(item.id, 'manufacturer', e.target.value)} /></label><label>تاريخ التصنيع<input type="date" value={line.manufacturedOn} onChange={(e) => updateLine(item.id, 'manufacturedOn', e.target.value)} /></label><label>تاريخ الانتهاء<input required type="date" min={tomorrowIso()} value={line.expiresOn} onChange={(e) => updateLine(item.id, 'expiresOn', e.target.value)} /></label></>}{Number(line.rejectedQuantity) > 0 && <label className="full-field">سبب الرفض<input required value={line.rejectionReason} maxLength={300} onChange={(e) => updateLine(item.id, 'rejectionReason', e.target.value)} /></label>}</div></article>; })}</div></div>{error ? <div className="alert alert-error full-field">{errorText(error)}</div> : null}<div className="form-hint full-field">سيتم ترحيل الكمية المقبولة إلى المخزون ضمن معاملة واحدة، وتسجيل الكمية المرفوضة وسبب الرفض دون إضافتها إلى الرصيد.</div><div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={onClose}>إلغاء</button><button className="primary-button" disabled={busy || !valid}>{busy ? 'جارٍ ترحيل الاستلام…' : 'تأكيد الاستلام والترحيل'}</button></div></form></section></div>;
}

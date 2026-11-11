import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { BookOpen, Plus } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

type Account = { id: string; branchId: string; accountCode: string; accountName: string; accountType: string; normalBalance: string; allowsPosting: boolean; active: boolean };
type JournalLine = { lineNumber: number; accountCode: string; accountName: string; debitAmount: number; creditAmount: number; memo?: string | null };
type Journal = { id: string; branchId: string; entryNumber: string; entryDate: string; description: string; sourceModule?: string | null; status: string; reversalOfEntryId?: string | null; totalDebit: number; totalCredit: number; lines: JournalLine[] };
type BalanceLine = { accountId: string; accountCode: string; accountName: string; accountType: string; normalBalance: string; debitMovement: number; creditMovement: number; closingDebit: number; closingCredit: number };
type TrialBalance = { branchId: string; asOfDate: string; currencyCode: string; accounts: BalanceLine[]; totalDebits: number; totalCredits: number; totalDebitBalances: number; totalCreditBalances: number; balanced: boolean };
type DraftLine = { accountCode: string; debitAmount: string; creditAmount: string; memo: string };

const errorText = (error: unknown) => error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'تعذر إكمال العملية.';
const amount = (value: number, currency: string) => `${Number(value ?? 0).toLocaleString('ar-YE', { maximumFractionDigits: 2 })} ${currency}`;
const accountTypeLabels: Record<string, string> = { ASSET: 'الأصول', LIABILITY: 'الالتزامات', EQUITY: 'حقوق الملكية', REVENUE: 'الإيرادات', EXPENSE: 'المصروفات' };

export default function AccountingPage() {
  const { token } = useAuth();
  const { branchId, branch } = useOutletContext<LayoutOutletContext>();
  const qc = useQueryClient();
  const [asOf, setAsOf] = useState(new Date().toISOString().slice(0, 10));
  const [showDraft, setShowDraft] = useState(false);
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [description, setDescription] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([
    { accountCode: '1100', debitAmount: '', creditAmount: '', memo: '' },
    { accountCode: '4100', debitAmount: '', creditAmount: '', memo: '' },
  ]);
  const accounts = useQuery({
    queryKey: ['accounting-accounts', branchId],
    queryFn: () => apiRequest<Account[]>(`/accounting/accounts?branchId=${branchId}`, token!),
    enabled: Boolean(token && branchId),
  });
  const trial = useQuery({
    queryKey: ['accounting-trial-balance', branchId, asOf],
    queryFn: () => apiRequest<TrialBalance>(`/accounting/trial-balance?branchId=${branchId}&asOf=${asOf}`, token!),
    enabled: Boolean(token && branchId && asOf),
  });
  const journals = useQuery({
    queryKey: ['accounting-journals', branchId],
    queryFn: () => apiRequest<Journal[]>(`/accounting/journals?branchId=${branchId}&limit=50`, token!),
    enabled: Boolean(token && branchId),
  });
  const create = useMutation({
    mutationFn: () => apiRequest<Journal>('/accounting/journals', token!, {
      method: 'POST',
      body: JSON.stringify({ branchId, entryDate: date, description, lines: lines.map((line) => ({
        accountCode: line.accountCode, debitAmount: Number(line.debitAmount || 0), creditAmount: Number(line.creditAmount || 0), memo: line.memo || undefined,
      })) }),
    }),
    onSuccess: async () => {
      setShowDraft(false); setDescription('');
      setLines([{ accountCode: '1100', debitAmount: '', creditAmount: '', memo: '' }, { accountCode: '4100', debitAmount: '', creditAmount: '', memo: '' }]);
      await qc.invalidateQueries({ queryKey: ['accounting-journals', branchId] });
    },
  });
  const reverse = useMutation({
    mutationFn: (id: string) => apiRequest<Journal>(`/accounting/journals/${id}/reversal`, token!, { method: 'POST' }),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['accounting-journals', branchId] }); },
  });
  const post = useMutation({
    mutationFn: (id: string) => apiRequest<Journal>(`/accounting/journals/${id}/post`, token!, { method: 'POST' }),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['accounting-journals', branchId] }); await qc.invalidateQueries({ queryKey: ['accounting-trial-balance', branchId] }); },
  });
  const accountOptions = accounts.data?.filter((account) => account.active && account.allowsPosting) ?? [];
  const debitTotal = useMemo(() => lines.reduce((sum, line) => sum + Number(line.debitAmount || 0), 0), [lines]);
  const creditTotal = useMemo(() => lines.reduce((sum, line) => sum + Number(line.creditAmount || 0), 0), [lines]);
  const balancedInput = debitTotal > 0 && Math.abs(debitTotal - creditTotal) < 0.005 && lines.length >= 2 && lines.every((line) => (Number(line.debitAmount || 0) > 0) !== (Number(line.creditAmount || 0) > 0));
  const currency = branch?.defaultCurrency ?? trial.data?.currencyCode ?? 'YER';

  const updateLine = (index: number, patch: Partial<DraftLine>) => setLines((current) => current.map((line, i) => i === index ? { ...line, ...patch } : line));
  const addLine = () => setLines((current) => current.length >= 50 ? current : [...current, { accountCode: accountOptions[0]?.accountCode ?? '', debitAmount: '', creditAmount: '', memo: '' }]);

  const trialColumns: ExportColumn<BalanceLine>[] = [
    { header: 'رمز الحساب', value: (row) => row.accountCode, width: 0.8, align: 'center' },
    { header: 'اسم الحساب', value: (row) => row.accountName, width: 1.8 },
    { header: 'التصنيف', value: (row) => accountTypeLabels[row.accountType] ?? row.accountType, width: 1 },
    { header: 'الحركة المدينة', value: (row) => amount(row.debitMovement, currency), width: 1.1 },
    { header: 'الحركة الدائنة', value: (row) => amount(row.creditMovement, currency), width: 1.1 },
    { header: 'الرصيد المدين', value: (row) => amount(row.closingDebit, currency), width: 1.1 },
    { header: 'الرصيد الدائن', value: (row) => amount(row.closingCredit, currency), width: 1.1 },
  ];
  const journalColumns: ExportColumn<Journal>[] = [
    { header: 'رقم القيد', value: (row) => row.entryNumber, width: 1 },
    { header: 'التاريخ', value: (row) => row.entryDate, width: 0.9, align: 'center' },
    { header: 'البيان', value: (row) => row.description, width: 2.2 },
    { header: 'المصدر', value: (row) => row.sourceModule ?? 'يدوي', width: 1 },
    { header: 'الحالة', value: (row) => row.status, width: 0.8, align: 'center' },
    { header: 'المدين', value: (row) => amount(row.totalDebit, currency), width: 1.1 },
    { header: 'الدائن', value: (row) => amount(row.totalCredit, currency), width: 1.1 },
  ];

  return <div className="module-page">
    <PageHeader
      eyebrow="GENERAL LEDGER & FINANCIAL CONTROL"
      title="المحاسبة العامة"
      description="دليل الحسابات والقيود اليومية وميزان المراجعة المتوازن."
      icon={BookOpen}
      meta={[`${journals.data?.length ?? 0} قيد`, trial.data?.balanced ? 'ميزان متوازن' : 'بانتظار المراجعة']}
      actions={
        <>
          <ExportMenu fileName="ميزان-المراجعة" title="تقرير ميزان المراجعة" subtitle={`الأرصدة حتى ${asOf} — ${branch?.name ?? 'الفرع الحالي'}`} columns={trialColumns} rows={trial.data?.accounts ?? []} label="تصدير الميزان" />
          <ExportMenu fileName="القيود-اليومية" title="تقرير القيود اليومية" subtitle={`آخر ${journals.data?.length ?? 0} قيد — ${branch?.name ?? 'الفرع الحالي'}`} columns={journalColumns} rows={journals.data ?? []} label="تصدير القيود" />
          <button className="primary-button" onClick={() => setShowDraft(true)} disabled={!accountOptions.length}><Plus size={16} /> قيد يومية يدوي</button>
        </>
      }
    />

    {(accounts.isError || trial.isError || journals.isError || create.isError || post.isError || reverse.isError) && <div className="alert alert-error">{errorText(accounts.error || trial.error || journals.error || create.error || post.error || reverse.error)}</div>}

    <section className="panel">
      <div className="panel-heading"><div><h2>ميزان المراجعة</h2><p>الأرصدة المتراكمة من القيود المرحلة حتى التاريخ المحدد.</p></div><label className="inline-filter">حتى تاريخ <input type="date" value={asOf} onChange={(event) => setAsOf(event.target.value)} /></label></div>
      {trial.isLoading ? <div className="empty-state">جارٍ تحميل ميزان المراجعة…</div> : trial.data ? <>
        <div className="finance-summary-grid">
          <Metric label="إجمالي المدين" value={amount(trial.data.totalDebits, currency)} />
          <Metric label="إجمالي الدائن" value={amount(trial.data.totalCredits, currency)} />
          <Metric label="أرصدة مدينة" value={amount(trial.data.totalDebitBalances, currency)} />
          <Metric label="أرصدة دائنة" value={amount(trial.data.totalCreditBalances, currency)} />
        </div>
        <div className={`balance-status ${trial.data.balanced ? 'balance-ok' : 'balance-error'}`}>{trial.data.balanced ? '✓ ميزان المراجعة متوازن' : '⚠ يوجد اختلاف في ميزان المراجعة'} · {trial.data.asOfDate}</div>
        <div className="table-wrap"><table className="data-table"><thead><tr><th>رمز الحساب</th><th>اسم الحساب</th><th>التصنيف</th><th>الحركة المدينة</th><th>الحركة الدائنة</th><th>الرصيد المدين</th><th>الرصيد الدائن</th></tr></thead><tbody>
          {trial.data.accounts.map((line) => <tr key={line.accountId}><td><strong>{line.accountCode}</strong></td><td>{line.accountName}</td><td>{accountTypeLabels[line.accountType] ?? line.accountType}</td><td>{amount(line.debitMovement, currency)}</td><td>{amount(line.creditMovement, currency)}</td><td>{amount(line.closingDebit, currency)}</td><td>{amount(line.closingCredit, currency)}</td></tr>)}
          {!trial.data.accounts.length && <tr><td colSpan={7}><div className="empty-mini">لا توجد حسابات في هذا الفرع.</div></td></tr>}
        </tbody></table></div>
      </> : <div className="empty-state">لا تتوفر بيانات ميزان المراجعة.</div>}
    </section>

    <section className="panel">
      <div className="panel-heading"><div><h2>دليل الحسابات</h2><p>حسابات الفرع التي يمكن ترحيل القيود عليها.</p></div><span className="panel-count">{accounts.data?.length ?? 0} حساب</span></div>
      {accounts.isLoading ? <div className="empty-state">جارٍ تحميل الحسابات…</div> : <div className="table-wrap"><table className="data-table"><thead><tr><th>الرمز</th><th>اسم الحساب</th><th>التصنيف</th><th>الطبيعة</th><th>السماح بالترحيل</th></tr></thead><tbody>
        {accounts.data?.map((account) => <tr key={account.id}><td><strong>{account.accountCode}</strong></td><td>{account.accountName}</td><td>{accountTypeLabels[account.accountType] ?? account.accountType}</td><td>{account.normalBalance === 'DEBIT' ? 'مدين' : 'دائن'}</td><td>{account.allowsPosting ? 'نعم' : 'حساب تجميعي'}</td></tr>)}
      </tbody></table></div>}
    </section>

    <section className="panel">
      <div className="panel-heading"><div><h2>دفتر اليومية</h2><p>القيود الآلية الناتجة عن الفواتير والمدفوعات والقيود اليدوية.</p></div><span className="panel-count">آخر 50 قيداً</span></div>
      {journals.isLoading ? <div className="empty-state">جارٍ تحميل دفتر اليومية…</div> : journals.data?.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>رقم القيد</th><th>التاريخ</th><th>البيان</th><th>المصدر</th><th>مدين</th><th>دائن</th><th>الحالة</th><th>إجراء</th></tr></thead><tbody>
        {journals.data.map((journal) => <tr key={journal.id}><td><strong>{journal.entryNumber}</strong></td><td>{journal.entryDate}</td><td>{journal.description}</td><td>{journal.sourceModule ?? '—'}</td><td>{amount(journal.totalDebit, currency)}</td><td>{amount(journal.totalCredit, currency)}</td><td><span className={`appointment-status ${journal.status === 'POSTED' ? 'status-completed' : 'status-pending'}`}>{journal.status === 'POSTED' ? 'مرحّل' : 'مسودة'}</span></td><td>{journal.status === 'DRAFT' && <button className="table-action" disabled={post.isPending} onClick={() => post.mutate(journal.id)}>ترحيل</button>}{journal.status === 'POSTED' && !journal.reversalOfEntryId && <button className="table-action" disabled={reverse.isPending} onClick={() => reverse.mutate(journal.id)}>إنشاء قيد عكسي</button>}</td></tr>)}
      </tbody></table></div> : <div className="empty-state">لا توجد قيود يومية بعد. ستظهر هنا القيود الناتجة عن إصدار الفواتير وتسجيل المدفوعات.</div>}
    </section>

    {showDraft && <div className="modal-backdrop"><section className="modal-card modal-wide">
      <div className="modal-header"><div><h2>إنشاء قيد يومية يدوي</h2><p>يُحفظ القيد كمسودة ثم يُرحّل بعد التحقق من توازن المدين والدائن.</p></div><button className="icon-button" onClick={() => setShowDraft(false)}>×</button></div>
      <form className="form-grid" onSubmit={(event) => { event.preventDefault(); create.mutate(); }}>
        <label>تاريخ القيد<input type="date" required value={date} onChange={(event) => setDate(event.target.value)} /></label>
        <label className="full-field">البيان<input required maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="وصف واضح لسبب القيد" /></label>
        <div className="full-field"><div className="panel-heading"><div><h3>بنود القيد</h3><p>يجب أن يحتوي كل سطر على مبلغ مدين أو دائن فقط.</p></div><button type="button" className="secondary-button small" onClick={addLine} disabled={lines.length >= 50}>＋ إضافة سطر</button></div>
          {lines.map((line, index) => <div className="journal-draft-line" key={index}>
            <label>الحساب<select required value={line.accountCode} onChange={(event) => updateLine(index, { accountCode: event.target.value })}><option value="">اختر الحساب</option>{accountOptions.map((account) => <option key={account.id} value={account.accountCode}>{account.accountCode} · {account.accountName}</option>)}</select></label>
            <label>مدين<input type="number" min="0" step="0.01" value={line.debitAmount} onChange={(event) => updateLine(index, { debitAmount: event.target.value, creditAmount: event.target.value ? '' : line.creditAmount })} /></label>
            <label>دائن<input type="number" min="0" step="0.01" value={line.creditAmount} onChange={(event) => updateLine(index, { creditAmount: event.target.value, debitAmount: event.target.value ? '' : line.debitAmount })} /></label>
            <label>ملاحظة<input maxLength={500} value={line.memo} onChange={(event) => updateLine(index, { memo: event.target.value })} /></label>
            <button type="button" className="icon-button danger-icon" aria-label="حذف السطر" disabled={lines.length <= 2} onClick={() => setLines((current) => current.filter((_, i) => i !== index))}>×</button>
          </div>)}
        </div>
        <div className="selected-option full-field">إجمالي المدين: {amount(debitTotal, currency)} · إجمالي الدائن: {amount(creditTotal, currency)} · {balancedInput ? 'القيد متوازن' : 'القيد غير متوازن بعد'}</div>
        {create.isError && <div className="alert alert-error full-field">{errorText(create.error)}</div>}
        <div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={() => setShowDraft(false)}>إلغاء</button><button className="primary-button" disabled={create.isPending || !description.trim() || !balancedInput}>{create.isPending ? 'جارٍ الحفظ…' : 'حفظ القيد كمسودة'}</button></div>
      </form>
    </section></div>}
  </div>;
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="finance-metric"><span>{label}</span><strong>{value}</strong></div>;
}

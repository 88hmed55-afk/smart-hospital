import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { Settings2, UserPlus, Users } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { Branch } from '../types/api';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

interface Department { id: string; branchId: string; code: string; name: string; departmentType: string; active: boolean; }
interface Staff { id: string; branchId: string; branchName: string; departmentId: string | null; departmentName: string | null; employeeNumber: string; firstName: string; lastName: string; workEmail?: string | null; phoneNumber?: string | null; jobTitle?: string | null; employmentStatus: string; hiredOn?: string | null; linkedUsername?: string | null; }
interface Role { code: string; name: string; description?: string | null; systemRole: boolean; }
interface Account { id: string; staffId: string | null; staffName?: string | null; username: string; accountStatus: string; mustChangePassword: boolean; mfaEnabled: boolean; lastLoginAt?: string | null; createdAt: string; roleCodes: string[]; branchIds: string[]; defaultBranchId?: string | null; }
type StaffCreate = { branchId: string; departmentId: string; employeeNumber: string; firstName: string; lastName: string; workEmail?: string; phoneNumber?: string; jobTitle?: string; hiredOn?: string };
type StaffUpdate = { departmentId?: string | null; firstName: string; lastName: string; workEmail?: string; phoneNumber?: string; jobTitle?: string; employmentStatus: 'ACTIVE' | 'ON_LEAVE' | 'SUSPENDED' | 'TERMINATED' };
type AccountCreate = { staffId: string; username: string; initialPassword: string; roleCodes: string[]; branchIds: string[]; defaultBranchId: string };

const errorText = (error: unknown) => error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'تعذر تنفيذ العملية.';
const strongPassword = (value: string) => value.length >= 12 && value.length <= 128 && /[A-Z]/.test(value) && /[a-z]/.test(value) && /\d/.test(value) && /[^A-Za-z0-9]/.test(value);
const roleLabels: Record<string, string> = { HOSPITAL_ADMIN: 'مدير النظام', HOSPITAL_MANAGER: 'مدير المستشفى', RECEPTIONIST: 'موظف استقبال', DOCTOR: 'طبيب', NURSE: 'ممرض', LAB_TECHNICIAN: 'فني مختبر', LAB_SUPERVISOR: 'مشرف مختبر', PHARMACIST: 'صيدلي', ACCOUNTANT: 'محاسب', INVENTORY_OFFICER: 'مسؤول المخزون' };
const statusLabels: Record<string, string> = { ACTIVE: 'نشط', ON_LEAVE: 'إجازة', SUSPENDED: 'موقوف مؤقتاً', TERMINATED: 'منتهي الخدمة', DISABLED: 'معطل' };

export default function AdministrationPage() {
  const { token, user } = useAuth();
  const { branchId, branch } = useOutletContext<LayoutOutletContext>();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'staff' | 'accounts' | 'roles'>('staff');
  const [search, setSearch] = useState('');
  const [staffStatus, setStaffStatus] = useState('');
  const [showStaffForm, setShowStaffForm] = useState(false);
  const [editingStaff, setEditingStaff] = useState<Staff | null>(null);
  const [showAccountForm, setShowAccountForm] = useState(false);
  const [staffForm, setStaffForm] = useState({ employeeNumber: '', firstName: '', lastName: '', departmentId: '', workEmail: '', phoneNumber: '', jobTitle: '', hiredOn: new Date().toISOString().slice(0, 10) });
  const [accountForm, setAccountForm] = useState({ staffId: '', username: '', initialPassword: '', confirmPassword: '', roleCodes: [] as string[], branchIds: [] as string[], defaultBranchId: branchId });
  const [mfaResetAccount, setMfaResetAccount] = useState<Account | null>(null);
  const [mfaResetReason, setMfaResetReason] = useState('');
  const [actionMessage, setActionMessage] = useState('');

  const branchesQuery = useQuery({ queryKey: ['admin-branches', token], queryFn: () => apiRequest<Branch[]>('/organization/branches', token!), enabled: Boolean(token) });
  const authorizedBranches = useMemo(() => (branchesQuery.data ?? []).filter((item) => user?.branchIds.includes(item.id)), [branchesQuery.data, user?.branchIds]);
  const departmentsQuery = useQuery({ queryKey: ['admin-departments', branchId], queryFn: () => apiRequest<Department[]>(`/organization/branches/${branchId}/departments`, token!), enabled: Boolean(token && branchId) });
  const staffQuery = useQuery({ queryKey: ['admin-staff', branchId, search, staffStatus], queryFn: () => apiRequest<Staff[]>(`/admin/staff?branchId=${branchId}&limit=200${search.trim() ? `&q=${encodeURIComponent(search.trim())}` : ''}${staffStatus ? `&status=${staffStatus}` : ''}`, token!), enabled: Boolean(token && branchId) });
  const accountsQuery = useQuery({ queryKey: ['admin-accounts', branchId], queryFn: () => apiRequest<Account[]>(`/admin/accounts?branchId=${branchId}&limit=200`, token!), enabled: Boolean(token && branchId) });
  const rolesQuery = useQuery({ queryKey: ['admin-roles'], queryFn: () => apiRequest<Role[]>('/admin/roles', token!), enabled: Boolean(token) });

  const updateStaff = useMutation({
    mutationFn: ({ id, body }: { id: string; body: StaffUpdate }) => apiRequest<Staff>(`/admin/staff/${id}`, token!, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: async () => { setEditingStaff(null); await qc.invalidateQueries({ queryKey: ['admin-staff'] }); await qc.invalidateQueries({ queryKey: ['admin-accounts'] }); setActionMessage('تم تحديث بيانات الموظف وحالة خدمته.'); },
  });
  const createStaff = useMutation({
    mutationFn: (body: StaffCreate) => apiRequest<Staff>('/admin/staff', token!, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: async () => { setShowStaffForm(false); setStaffForm({ employeeNumber: '', firstName: '', lastName: '', departmentId: '', workEmail: '', phoneNumber: '', jobTitle: '', hiredOn: new Date().toISOString().slice(0, 10) }); await qc.invalidateQueries({ queryKey: ['admin-staff'] }); setActionMessage('تم تسجيل الموظف. يمكنك الآن إنشاء حساب نظام له.'); },
  });
  const provisionAccount = useMutation({
    mutationFn: (body: AccountCreate) => apiRequest<Account>('/admin/accounts', token!, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: async () => { setShowAccountForm(false); setAccountForm({ staffId: '', username: '', initialPassword: '', confirmPassword: '', roleCodes: [], branchIds: [branchId], defaultBranchId: branchId }); await qc.invalidateQueries({ queryKey: ['admin-accounts'] }); await qc.invalidateQueries({ queryKey: ['admin-staff'] }); setActionMessage('تم إنشاء الحساب. سلّم كلمة المرور الأولية للموظف بطريقة آمنة؛ سيُطلب منه تغييرها عند أول دخول.'); },
  });
  const accountStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'ACTIVE' | 'DISABLED' }) => apiRequest<Account>(`/admin/accounts/${id}/status`, token!, { method: 'PATCH', body: JSON.stringify({ branchId, accountStatus: status }) }),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['admin-accounts'] }); setActionMessage('تم تحديث حالة الحساب.'); },
  });
  const resetMfa = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => apiRequest<void>(`/admin/accounts/${id}/mfa-reset`, token!, { method: 'PATCH', body: JSON.stringify({ branchId, reason }) }),
    onSuccess: async () => { setMfaResetAccount(null); setMfaResetReason(''); await qc.invalidateQueries({ queryKey: ['admin-accounts'] }); setActionMessage('تمت إعادة تعيين العامل الثاني. أُبطلت جلسات الحساب وسيُطلب تسجيل جهاز جديد عند الدخول.'); },
  });

  const activeStaffWithoutAccount = useMemo(() => (staffQuery.data ?? []).filter((person) => person.employmentStatus === 'ACTIVE' && !person.linkedUsername), [staffQuery.data]);
  const passwordsMatch = accountForm.initialPassword.length > 0 && accountForm.initialPassword === accountForm.confirmPassword;
  const canProvision = Boolean(accountForm.staffId && accountForm.username.trim().length >= 3 && strongPassword(accountForm.initialPassword) && passwordsMatch && accountForm.roleCodes.length && accountForm.branchIds.length && accountForm.defaultBranchId);

  const staffExportColumns: ExportColumn<Staff>[] = [
    { header: 'الرقم الوظيفي', value: (row) => row.employeeNumber, width: 1 },
    { header: 'اسم الموظف', value: (row) => `${row.firstName} ${row.lastName}`, width: 1.7 },
    { header: 'الفرع', value: (row) => row.branchName, width: 1.3 },
    { header: 'القسم', value: (row) => row.departmentName ?? '—', width: 1.2 },
    { header: 'المسمى الوظيفي', value: (row) => row.jobTitle ?? '—', width: 1.3 },
    { header: 'الهاتف', value: (row) => row.phoneNumber ?? '—', width: 1.1, align: 'left' },
    { header: 'البريد', value: (row) => row.workEmail ?? '—', width: 1.5, align: 'left' },
    { header: 'الحالة', value: (row) => statusLabels[row.employmentStatus] ?? row.employmentStatus, width: 0.9, align: 'center' },
    { header: 'حساب الدخول', value: (row) => row.linkedUsername ?? 'لا يوجد', width: 1.1, align: 'center' },
  ];
  const accountExportColumns: ExportColumn<Account>[] = [
    { header: 'اسم المستخدم', value: (row) => row.username, width: 1.2, align: 'left' },
    { header: 'الموظف المرتبط', value: (row) => row.staffName ?? '—', width: 1.6 },
    { header: 'حالة الحساب', value: (row) => statusLabels[row.accountStatus] ?? row.accountStatus, width: 0.9, align: 'center' },
    { header: 'الأدوار', value: (row) => row.roleCodes.map((code) => roleLabels[code] ?? code).join('، '), width: 2.2 },
    { header: 'عدد الفروع', value: (row) => row.branchIds.length, width: 0.7, align: 'center' },
    { header: 'MFA', value: (row) => (row.mfaEnabled ? 'مفعّل' : 'غير مفعّل'), width: 0.7, align: 'center' },
    { header: 'تغيير كلمة المرور', value: (row) => (row.mustChangePassword ? 'مطلوب' : 'مكتمل'), width: 0.9, align: 'center' },
    { header: 'آخر دخول', value: (row) => (row.lastLoginAt ? new Intl.DateTimeFormat('ar-YE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(row.lastLoginAt)) : '—'), width: 1.4 },
  ];
  const roleExportColumns: ExportColumn<Role>[] = [
    { header: 'الرمز', value: (row) => row.code, width: 1.2, align: 'left' },
    { header: 'الدور', value: (row) => row.name, width: 1.5 },
    { header: 'الوصف', value: (row) => row.description ?? '—', width: 2.6 },
    { header: 'دور نظام', value: (row) => (row.systemRole ? 'نعم' : 'لا'), width: 0.7, align: 'center' },
  ];

  function submitStaff(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createStaff.mutate({ branchId, departmentId: staffForm.departmentId, employeeNumber: staffForm.employeeNumber.trim(), firstName: staffForm.firstName.trim(), lastName: staffForm.lastName.trim(), workEmail: staffForm.workEmail || undefined, phoneNumber: staffForm.phoneNumber || undefined, jobTitle: staffForm.jobTitle || undefined, hiredOn: staffForm.hiredOn || undefined });
  }
  function submitAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canProvision) return;
    provisionAccount.mutate({ staffId: accountForm.staffId, username: accountForm.username.trim(), initialPassword: accountForm.initialPassword, roleCodes: accountForm.roleCodes, branchIds: accountForm.branchIds, defaultBranchId: accountForm.defaultBranchId });
  }
  function toggleRole(code: string) { setAccountForm((current) => ({ ...current, roleCodes: current.roleCodes.includes(code) ? current.roleCodes.filter((item) => item !== code) : [...current.roleCodes, code] })); }
  function toggleBranch(id: string) {
    setAccountForm((current) => {
      const branchIds = current.branchIds.includes(id) ? current.branchIds.filter((item) => item !== id) : [...current.branchIds, id];
      return { ...current, branchIds, defaultBranchId: branchIds.includes(current.defaultBranchId) ? current.defaultBranchId : (branchIds[0] ?? '') };
    });
  }

  return <div className="module-page admin-page">
    <PageHeader
      eyebrow="IDENTITY · STAFF · ACCESS CONTROL"
      title="إدارة النظام والموظفين"
      description="ملفات الموظفين وحسابات الدخول والأدوار ونطاق الفروع."
      icon={Settings2}
      meta={[`${(staffQuery.data ?? []).length} موظف`, `${(accountsQuery.data ?? []).length} حساب`]}
      actions={
        <>
          {tab === 'staff' && <ExportMenu fileName="دليل-الموظفين" title="تقرير دليل الموظفين" subtitle={`الموظفون — ${branch?.name ?? 'الفرع الحالي'}`} columns={staffExportColumns} rows={staffQuery.data ?? []} />}
          {tab === 'accounts' && <ExportMenu fileName="حسابات-المستخدمين" title="تقرير حسابات المستخدمين" subtitle={`الحسابات — ${branch?.name ?? 'الفرع الحالي'}`} meta={[{ label: 'ملاحظة', value: 'لا يتضمن التقرير كلمات المرور أو أسرار MFA' }]} columns={accountExportColumns} rows={accountsQuery.data ?? []} />}
          {tab === 'roles' && <ExportMenu fileName="كتالوج-الأدوار" title="تقرير كتالوج الأدوار" subtitle="الأدوار والصلاحيات المعرّفة في النظام" columns={roleExportColumns} rows={rolesQuery.data ?? []} />}
          <button className="secondary-button" onClick={() => { setAccountForm((current) => ({ ...current, staffId: '', username: '', initialPassword: '', confirmPassword: '', roleCodes: [], branchIds: [branchId], defaultBranchId: branchId })); setShowAccountForm(true); }} disabled={!activeStaffWithoutAccount.length}><UserPlus size={16} /> حساب مستخدم</button>
          <button className="primary-button" onClick={() => setShowStaffForm(true)}><Users size={16} /> موظف جديد</button>
        </>
      }
    />

    {actionMessage && <div className="alert alert-success"><span>{actionMessage}</span><button className="icon-button" onClick={() => setActionMessage('')} aria-label="إغلاق">×</button></div>}
    {(staffQuery.isError || accountsQuery.isError || rolesQuery.isError || createStaff.isError || updateStaff.isError || provisionAccount.isError || accountStatus.isError || resetMfa.isError) && <div className="alert alert-error">{errorText(staffQuery.error || accountsQuery.error || rolesQuery.error || createStaff.error || updateStaff.error || provisionAccount.error || accountStatus.error || resetMfa.error)}</div>}

    <section className="admin-overview-grid">
      <article className="stat-card"><span>الموظفون في الفرع</span><strong>{(staffQuery.data ?? []).length.toLocaleString('ar-YE')}</strong><small>السجلات المطابقة للتصفية</small></article>
      <article className="stat-card"><span>حسابات الدخول</span><strong>{(accountsQuery.data ?? []).length.toLocaleString('ar-YE')}</strong><small>حسابات مرئية ضمن الفرع الحالي</small></article>
      <article className="stat-card"><span>موظفون دون حساب</span><strong>{activeStaffWithoutAccount.length.toLocaleString('ar-YE')}</strong><small>موظفون نشطون يمكن ربط حساب لهم</small></article>
    </section>

    <section className="panel">
      <div className="admin-tabs" role="tablist" aria-label="أقسام الإدارة">
        <button className={tab === 'staff' ? 'admin-tab active' : 'admin-tab'} onClick={() => setTab('staff')}>الموظفون</button>
        <button className={tab === 'accounts' ? 'admin-tab active' : 'admin-tab'} onClick={() => setTab('accounts')}>حسابات المستخدمين</button>
        <button className={tab === 'roles' ? 'admin-tab active' : 'admin-tab'} onClick={() => setTab('roles')}>كتالوج الأدوار</button>
      </div>

      {tab === 'staff' && <>
        <div className="panel-heading"><div><h2>دليل الموظفين</h2><p>إدارة الارتباط الوظيفي بالقسم والفرع وحالة الخدمة.</p></div><div className="panel-tools"><input className="compact-input" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="بحث بالاسم أو الرقم الوظيفي" /><select className="compact-select" value={staffStatus} onChange={(event) => setStaffStatus(event.target.value)}><option value="">كل الحالات</option><option value="ACTIVE">نشط</option><option value="ON_LEAVE">إجازة</option><option value="SUSPENDED">موقوف</option><option value="TERMINATED">منتهي الخدمة</option></select><button className="icon-button" onClick={() => void staffQuery.refetch()} title="تحديث">↻</button></div></div>
        {staffQuery.isLoading ? <div className="empty-state">جارٍ تحميل دليل الموظفين…</div> : staffQuery.data?.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>الرقم الوظيفي</th><th>اسم الموظف</th><th>القسم</th><th>المسمى الوظيفي</th><th>الحالة</th><th>حساب الدخول</th></tr></thead><tbody>{staffQuery.data.map((person) => <tr key={person.id}><td><strong>{person.employeeNumber}</strong></td><td>{person.firstName} {person.lastName}</td><td>{person.departmentName ?? '—'}</td><td>{person.jobTitle ?? '—'}</td><td><span className={`appointment-status ${person.employmentStatus === 'ACTIVE' ? 'status-active' : 'status-inactive'}`}>{statusLabels[person.employmentStatus] ?? person.employmentStatus}</span></td><td><div className="inline-actions">{person.linkedUsername ? <span className="admin-linked-user">{person.linkedUsername}</span> : <button className="table-action" onClick={() => { setAccountForm((current) => ({ ...current, staffId: person.id, username: '', initialPassword: '', confirmPassword: '', roleCodes: [], branchIds: [branchId], defaultBranchId: branchId })); setShowAccountForm(true); }}>إنشاء حساب</button>}<button className="table-action" onClick={() => setEditingStaff(person)}>تعديل</button></div></td></tr>)}</tbody></table></div> : <div className="empty-state">لا توجد سجلات موظفين مطابقة في هذا الفرع.</div>}
      </>}

      {tab === 'accounts' && <>
        <div className="panel-heading"><div><h2>حسابات النظام</h2><p>الحسابات المرتبطة بموظفين أو هويات إدارية، مع أدوارها وفروع الوصول والمصادقة الثنائية. كلمات المرور مُدارة داخل التطبيق وتُخزَّن كتجزئة PBKDF2 فقط.</p></div><button className="icon-button" onClick={() => void accountsQuery.refetch()} title="تحديث">↻</button></div>
        {accountsQuery.isLoading ? <div className="empty-state">جارٍ تحميل الحسابات…</div> : accountsQuery.data?.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>اسم الدخول</th><th>الموظف</th><th>الأدوار</th><th>الفروع</th><th>المصادقة الثنائية</th><th>الحالة</th><th>آخر دخول</th><th>إجراءات</th></tr></thead><tbody>{accountsQuery.data.map((account) => <tr key={account.id}>
          <td><strong>{account.username}</strong>{account.mustChangePassword && <small className="admin-subline">بانتظار تغيير كلمة المرور الأولية</small>}</td>
          <td>{account.staffName ?? 'حساب إداري'}</td>
          <td><div className="admin-role-list">{account.roleCodes.map((code) => <span key={code}>{roleLabels[code] ?? code}</span>)}</div></td>
          <td>{account.branchIds.length} فرع</td>
          <td><span className={`appointment-status ${account.mfaEnabled ? 'status-active' : 'status-inactive'}`}>{account.mfaEnabled ? 'مفعّلة' : 'غير مفعّلة'}</span></td>
          <td><span className={`appointment-status ${account.accountStatus === 'ACTIVE' ? 'status-active' : 'status-inactive'}`}>{statusLabels[account.accountStatus] ?? account.accountStatus}</span></td>
          <td>{account.lastLoginAt ? new Date(account.lastLoginAt).toLocaleString('ar-YE') : 'لم يسجل دخولاً'}</td>
          <td><div className="inline-actions"><button className="table-action" disabled={accountStatus.isPending} onClick={() => accountStatus.mutate({ id: account.id, status: account.accountStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}>{account.accountStatus === 'ACTIVE' ? 'تعطيل' : 'تفعيل'}</button>{account.mfaEnabled && account.id !== user?.userId && <button className="table-action" onClick={() => { setMfaResetReason(''); setMfaResetAccount(account); }}>إعادة تعيين MFA</button>}</div></td>
        </tr>)}</tbody></table></div> : <div className="empty-state">لا توجد حسابات مرتبطة بالفرع الحالي.</div>}
        <div className="admin-security-note"><strong>معلومة أمنية:</strong> كلمة المرور الأولية تُدخل هنا مرة واحدة وتُخزَّن كتجزئة PBKDF2 فقط، ويُطلب تغييرها عند أول دخول. إعادة تعيين العامل الثاني (MFA) تتطلب سبباً موثقاً وتُبطل جميع جلسات الحساب؛ ولا يمكن للمسؤول إعادة تعيين عامله الثاني بنفسه، بل ينفذها مسؤول آخر.</div>
      </>}

      {tab === 'roles' && <>
        <div className="panel-heading"><div><h2>كتالوج الأدوار</h2><p>الأدوار المعرفة على مستوى المنصة، والتي تُمنح للحسابات وتُستخدم في فرض الصلاحيات ونطاق الفروع.</p></div></div>
        {rolesQuery.isLoading ? <div className="empty-state">جارٍ تحميل الأدوار…</div> : <div className="admin-role-catalog">{(rolesQuery.data ?? []).map((role) => <article className="admin-role-card" key={role.code}><div><strong>{roleLabels[role.code] ?? role.name}</strong><small>{role.code}</small></div><p>{role.description || 'لا يوجد وصف إضافي.'}</p><span className="role-system-label">{role.systemRole ? 'دور نظام' : 'دور مخصص'}</span></article>)}</div>}
      </>}
    </section>

    {editingStaff && <StaffEditModal staff={editingStaff} departments={departmentsQuery.data ?? []} busy={updateStaff.isPending} onClose={() => setEditingStaff(null)} onSubmit={(body) => updateStaff.mutate({ id: editingStaff.id, body })} error={updateStaff.error} />}

    {showStaffForm && <div className="modal-backdrop"><section className="modal-card"><div className="modal-header"><div><h2>تسجيل موظف جديد</h2><p>يُسجل الموظف في الفرع الحالي.</p></div><button className="icon-button" onClick={() => setShowStaffForm(false)}>×</button></div><form className="form-grid two-cols" onSubmit={submitStaff}>
      <label>الرقم الوظيفي<input required maxLength={32} value={staffForm.employeeNumber} onChange={(e) => setStaffForm({ ...staffForm, employeeNumber: e.target.value })} /></label>
      <label>القسم<select required value={staffForm.departmentId} onChange={(e) => setStaffForm({ ...staffForm, departmentId: e.target.value })}><option value="">اختر القسم</option>{(departmentsQuery.data ?? []).map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}</select></label>
      <label>الاسم الأول<input required maxLength={80} value={staffForm.firstName} onChange={(e) => setStaffForm({ ...staffForm, firstName: e.target.value })} /></label>
      <label>اسم العائلة<input required maxLength={80} value={staffForm.lastName} onChange={(e) => setStaffForm({ ...staffForm, lastName: e.target.value })} /></label>
      <label>المسمى الوظيفي<input maxLength={100} value={staffForm.jobTitle} onChange={(e) => setStaffForm({ ...staffForm, jobTitle: e.target.value })} /></label>
      <label>تاريخ التوظيف<input type="date" value={staffForm.hiredOn} onChange={(e) => setStaffForm({ ...staffForm, hiredOn: e.target.value })} /></label>
      <label>البريد الوظيفي<input type="email" maxLength={254} value={staffForm.workEmail} onChange={(e) => setStaffForm({ ...staffForm, workEmail: e.target.value })} /></label>
      <label>رقم الهاتف<input dir="ltr" maxLength={32} value={staffForm.phoneNumber} onChange={(e) => setStaffForm({ ...staffForm, phoneNumber: e.target.value })} /></label>
      <div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={() => setShowStaffForm(false)}>إلغاء</button><button className="primary-button" disabled={createStaff.isPending || !departmentsQuery.data?.length}>{createStaff.isPending ? 'جارٍ الحفظ…' : 'حفظ الموظف'}</button></div>
    </form></section></div>}

    {showAccountForm && <div className="modal-backdrop"><section className="modal-card modal-wide"><div className="modal-header"><div><h2>إنشاء حساب دخول لموظف</h2><p>يُنشأ الحساب داخل التطبيق بكلمة مرور أولية يغيّرها الموظف عند أول دخول.</p></div><button className="icon-button" onClick={() => setShowAccountForm(false)}>×</button></div><form className="form-grid two-cols" onSubmit={submitAccount}>
      <label>الموظف<select required value={accountForm.staffId} onChange={(e) => setAccountForm({ ...accountForm, staffId: e.target.value })}><option value="">اختر موظفاً نشطاً</option>{activeStaffWithoutAccount.map((person) => <option key={person.id} value={person.id}>{person.employeeNumber} · {person.firstName} {person.lastName}</option>)}</select></label>
      <label>اسم الدخول<input required minLength={3} maxLength={80} pattern="[A-Za-z0-9._-]{3,80}" dir="ltr" value={accountForm.username} onChange={(e) => setAccountForm({ ...accountForm, username: e.target.value })} placeholder="new.user" autoComplete="off" /></label>
      <label>كلمة المرور الأولية<input required type="password" minLength={12} maxLength={128} dir="ltr" value={accountForm.initialPassword} onChange={(e) => setAccountForm({ ...accountForm, initialPassword: e.target.value })} autoComplete="new-password" /></label>
      <label>تأكيد كلمة المرور<input required type="password" minLength={12} maxLength={128} dir="ltr" value={accountForm.confirmPassword} onChange={(e) => setAccountForm({ ...accountForm, confirmPassword: e.target.value })} autoComplete="new-password" /></label>
      {accountForm.initialPassword && !strongPassword(accountForm.initialPassword) && <div className="alert alert-error full-field">يجب أن تكون كلمة المرور 12–128 حرفاً وتتضمن حرفاً كبيراً وصغيراً ورقماً ورمزاً.</div>}
      {accountForm.confirmPassword && !passwordsMatch && <div className="alert alert-error full-field">كلمتا المرور غير متطابقتين.</div>}
      <div className="full-field admin-role-picker"><strong>الأدوار</strong><div className="admin-checkbox-grid">{(rolesQuery.data ?? []).map((role) => <label key={role.code}><input type="checkbox" checked={accountForm.roleCodes.includes(role.code)} onChange={() => toggleRole(role.code)} /><span>{roleLabels[role.code] ?? role.name}</span></label>)}</div></div>
      <div className="full-field admin-role-picker"><strong>الفروع المصرح بها</strong><div className="admin-checkbox-grid">{authorizedBranches.map((item) => <label key={item.id}><input type="checkbox" checked={accountForm.branchIds.includes(item.id)} onChange={() => toggleBranch(item.id)} /><span>{item.name}</span></label>)}</div><label className="admin-default-branch">الفرع الافتراضي<select value={accountForm.defaultBranchId} onChange={(e) => setAccountForm({ ...accountForm, defaultBranchId: e.target.value })}>{authorizedBranches.filter((item) => accountForm.branchIds.includes(item.id)).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label></div>
      <div className="full-field admin-security-note"><strong>تسليم آمن:</strong> تُخزَّن كلمة المرور الأولية كتجزئة PBKDF2 فقط ولا يمكن استرجاعها. سلّمها للموظف عبر قناة موثوقة، وسيُطلب منه تغييرها مباشرة عند أول دخول. فعّل المصادقة الثنائية لكل حساب قبل منحه وصولاً لبيانات المرضى.</div>
      {provisionAccount.isError && <div className="alert alert-error full-field">{errorText(provisionAccount.error)}</div>}
      <div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={() => setShowAccountForm(false)}>إلغاء</button><button className="primary-button" disabled={!canProvision || provisionAccount.isPending}>{provisionAccount.isPending ? 'جارٍ إنشاء الحساب…' : 'إنشاء الحساب'}</button></div>
    </form></section></div>}

    {mfaResetAccount && <div className="modal-backdrop"><section className="modal-card"><div className="modal-header"><div><h2>إعادة تعيين العامل الثاني</h2><p>{mfaResetAccount.username} · {mfaResetAccount.staffName ?? 'حساب إداري'}</p></div><button className="icon-button" onClick={() => { setMfaResetAccount(null); setMfaResetReason(''); }}>×</button></div><form className="form-grid" onSubmit={(event) => { event.preventDefault(); resetMfa.mutate({ id: mfaResetAccount.id, reason: mfaResetReason.trim() }); }}>
      <div className="alert alert-error">سيُحذف سر TOTP ورموز الاسترداد، وتُبطل جميع جلسات الحساب. لا تنفّذ هذا الإجراء قبل التحقق من هوية الموظف خارج النظام (مكالمة فيديو مع المدير المباشر مثلاً).</div>
      <label>سبب إعادة التعيين (10–400 حرف)<textarea required minLength={10} maxLength={400} rows={4} value={mfaResetReason} onChange={(event) => setMfaResetReason(event.target.value)} placeholder="مثال: فقد الموظف هاتفه وتم التحقق من هويته هاتفياً مع مدير القسم." /></label>
      {resetMfa.isError && <div className="alert alert-error">{errorText(resetMfa.error)}</div>}
      <div className="modal-actions"><button type="button" className="secondary-button" onClick={() => { setMfaResetAccount(null); setMfaResetReason(''); }}>إلغاء</button><button className="primary-button" disabled={resetMfa.isPending || mfaResetReason.trim().length < 10 || mfaResetReason.trim().length > 400}>{resetMfa.isPending ? 'جارٍ التنفيذ…' : 'تأكيد إعادة التعيين'}</button></div>
    </form></section></div>}
  </div>;
}


function StaffEditModal({ staff, departments, busy, error, onClose, onSubmit }: {
  staff: Staff; departments: Department[]; busy: boolean; error: unknown; onClose: () => void; onSubmit: (body: StaffUpdate) => void;
}) {
  const [form, setForm] = useState({ departmentId: staff.departmentId ?? '', firstName: staff.firstName, lastName: staff.lastName, workEmail: staff.workEmail ?? '', phoneNumber: staff.phoneNumber ?? '', jobTitle: staff.jobTitle ?? '', employmentStatus: staff.employmentStatus as StaffUpdate['employmentStatus'] });
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); onSubmit({ departmentId: form.departmentId || null, firstName: form.firstName.trim(), lastName: form.lastName.trim(), workEmail: form.workEmail || undefined, phoneNumber: form.phoneNumber || undefined, jobTitle: form.jobTitle || undefined, employmentStatus: form.employmentStatus }); }
  return <div className="modal-backdrop"><section className="modal-card"><div className="modal-header"><div><h2>تعديل ملف الموظف</h2><p>{staff.employeeNumber} · {staff.firstName} {staff.lastName}</p></div><button className="icon-button" onClick={onClose}>×</button></div><form className="form-grid two-cols" onSubmit={submit}>
    <label>الاسم الأول<input required maxLength={80} value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} /></label>
    <label>اسم العائلة<input required maxLength={80} value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} /></label>
    <label>القسم<select value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: e.target.value })}><option value="">بدون قسم</option>{departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}</select></label>
    <label>حالة الخدمة<select value={form.employmentStatus} onChange={(e) => setForm({ ...form, employmentStatus: e.target.value as StaffUpdate['employmentStatus'] })}><option value="ACTIVE">نشط</option><option value="ON_LEAVE">إجازة</option><option value="SUSPENDED">موقوف مؤقتاً</option><option value="TERMINATED">منتهي الخدمة</option></select></label>
    <label>المسمى الوظيفي<input maxLength={100} value={form.jobTitle} onChange={(e) => setForm({ ...form, jobTitle: e.target.value })} /></label>
    <label>البريد الوظيفي<input type="email" maxLength={254} value={form.workEmail} onChange={(e) => setForm({ ...form, workEmail: e.target.value })} /></label>
    <label className="full-field">رقم الهاتف<input dir="ltr" maxLength={32} value={form.phoneNumber} onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} /></label>
    {error ? <div className="alert alert-error full-field">{errorText(error)}</div> : null}
    <div className="modal-actions full-field"><button type="button" className="secondary-button" onClick={onClose}>إلغاء</button><button className="primary-button" disabled={busy}>{busy ? 'جارٍ التحديث…' : 'حفظ التعديلات'}</button></div>
  </form></section></div>;
}

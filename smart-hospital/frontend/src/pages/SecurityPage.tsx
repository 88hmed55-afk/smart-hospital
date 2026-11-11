import { useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { KeyRound, ShieldCheck } from 'lucide-react';
import { apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import type { MfaRecoveryCodes, MfaStatus } from '../types/api';

const errorText = (error: unknown) => error instanceof Error ? error.message : 'تعذر تنفيذ العملية.';

/** Self-service MFA posture, recovery-code rotation and disable with re-authentication. */
export default function SecurityPage() {
  const { token, user, signOut } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [newCodes, setNewCodes] = useState<string[] | null>(null);

  const status = useQuery({
    queryKey: ['mfa-status'],
    queryFn: () => apiRequest<MfaStatus>('/auth/mfa/status', token!),
    enabled: Boolean(token),
  });

  async function regenerate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    setError('');
    setBusy(true);
    try {
      const result = await apiRequest<MfaRecoveryCodes>('/auth/mfa/recovery-codes/regenerate', token, {
        method: 'POST',
        body: JSON.stringify({ password, code }),
      });
      setNewCodes(result.recoveryCodes);
      setPassword('');
      setCode('');
      await queryClient.invalidateQueries({ queryKey: ['mfa-status'] });
    } catch (reason) {
      setError(errorText(reason));
    } finally {
      setBusy(false);
    }
  }

  async function disable(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    if (!window.confirm('سيتم تعطيل المصادقة الثنائية وإبطال كل الجلسات. هل تريد المتابعة؟')) return;
    setError('');
    setBusy(true);
    try {
      await apiRequest<void>('/auth/mfa/disable', token, {
        method: 'POST',
        body: JSON.stringify({ password, code }),
      });
      signOut();
      navigate('/login', { replace: true, state: { mfaDisabled: true } });
    } catch (reason) {
      setError(errorText(reason));
    } finally {
      setBusy(false);
    }
  }

  return <div className="module-page governance-page">
    <PageHeader
      eyebrow="ACCOUNT SECURITY"
      title="الأمان والمصادقة الثنائية"
      description="إدارة العامل الثاني لحسابك: التسجيل، رموز الاسترداد، والتعطيل بعد إعادة التحقق."
      icon={ShieldCheck}
      meta={[status.data?.enabled ? 'الحساب محمي بـMFA' : 'MFA غير مفعّل']}
      actions={<Link className="primary-button" to="/mfa-setup"><KeyRound size={16} /> إعداد جهاز جديد</Link>}
    />
    {error && <div className="alert alert-error" role="alert">{error}</div>}
    {newCodes && <div className="panel">
      <div className="panel-heading"><div><h2>رموز استرداد جديدة</h2><p>تم إبطال الرموز السابقة. احفظ هذه الرموز الآن؛ لن تُعرض مرة أخرى.</p></div></div>
      <div className="recovery-code-grid" dir="ltr">{newCodes.map((item) => <code key={item}>{item}</code>)}</div>
      <button type="button" className="secondary-button" onClick={() => setNewCodes(null)}>لقد حفظتها</button>
    </div>}
    <div className="settings-grid">
      <article className="panel setting-card">
        <div className="setting-card-header"><div><h2>حالة الحساب</h2><code dir="ltr">{user?.username}</code></div><span className={`setting-source ${status.data?.enabled ? 'setting-source-branch' : 'setting-source-default'}`}>{status.data?.enabled ? 'محمي' : 'غير مفعّل'}</span></div>
        <p>المصادقة الثنائية تحمي الوصول حتى لو تسربت كلمة المرور. يُنصح بتفعيلها لكل حسابات المستشفى، وتُفرض تلقائياً عند ضبط سياسة الأدوار على الخادم.</p>
        {status.isLoading ? <div className="empty-state">جارٍ تحميل الحالة…</div> : status.data?.enabled ? <>
          <div className="setting-current"><span>تاريخ التفعيل</span><strong dir="ltr">{status.data.enrolledAt ? new Date(status.data.enrolledAt).toLocaleString('ar-YE') : '—'}</strong></div>
          <div className="setting-current"><span>رموز الاسترداد المتبقية</span><strong>{status.data.recoveryCodesRemaining} / 10</strong></div>
          {status.data.recoveryCodesRemaining <= 3 && <div className="alert alert-error" role="status">عدد رموز الاسترداد منخفض. أنشئ رموزاً جديدة قبل فقدان الوصول.</div>}
        </> : <div className="empty-state">لم يتم تسجيل جهاز مصادقة لهذا الحساب بعد.</div>}
      </article>
      <article className="panel setting-card">
        <div className="setting-card-header"><div><h2>إعادة التحقق</h2><code dir="ltr">PASSWORD + TOTP</code></div></div>
        <p>عمليات إعادة إنشاء الرموز أو تعطيل المصادقة تتطلب كلمة المرور الحالية ورمزاً صالحاً من تطبيق المصادقة.</p>
        <form onSubmit={regenerate} className="login-form">
          <label>كلمة المرور الحالية<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
          <label>رمز المصادقة<input value={code} onChange={(event) => setCode(event.target.value)} placeholder="123456" autoComplete="one-time-code" inputMode="numeric" required /></label>
          <button className="primary-button" disabled={busy || !status.data?.enabled}>{busy ? 'جارٍ التنفيذ…' : 'إنشاء رموز استرداد جديدة'}</button>
        </form>
      </article>
    </div>
    {status.data?.enabled && <div className="panel">
      <div className="panel-heading"><div><h2>تعطيل المصادقة الثنائية</h2><p>يُستخدم عند استبدال الجهاز أو عند تعذر الوصول إليه. تُبطَل كل الجلسات بعد التعطيل.</p></div></div>
      <form onSubmit={disable} className="login-form">
        <label>كلمة المرور الحالية<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
        <label>رمز المصادقة أو الاسترداد<input value={code} onChange={(event) => setCode(event.target.value)} placeholder="123456 أو ABCDE-FGHIJ" autoComplete="one-time-code" required /></label>
        <button className="secondary-button danger-text" disabled={busy}>تعطيل المصادقة الثنائية</button>
      </form>
    </div>}
    <p className="governance-note">كل عمليات التسجيل والتفعيل والتعطيل واستخدام رموز الاسترداد تُسجَّل في سجل التدقيق. لا تُشارك رموز الاسترداد مع أي شخص، بما في ذلك موظفو الدعم.</p>
  </div>;
}

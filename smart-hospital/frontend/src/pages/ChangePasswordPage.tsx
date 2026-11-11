import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { KeyRound, ShieldCheck, TimerReset } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import Brand from '../components/Brand';

const PASSWORD_RULE = 'من 12 إلى 128 حرفاً، وتشمل حرفاً كبيراً وصغيراً ورقماً ورمزاً واحداً على الأقل.';

/**
 * Forced credential rotation screen. Shown before any other page while the account
 * carries must_change_password = TRUE and the server enforces rotation.
 *
 * A successful change revokes every session (including this one), so the user is sent
 * back to the login screen to authenticate with the new password.
 */
export default function ChangePasswordPage() {
  const { user, changePassword, signOut } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    if (newPassword.length < 12) {
      setError(`كلمة المرور الجديدة قصيرة جداً. ${PASSWORD_RULE}`);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('تأكيد كلمة المرور لا يطابق كلمة المرور الجديدة.');
      return;
    }
    if (newPassword === currentPassword) {
      setError('يجب أن تختلف كلمة المرور الجديدة عن الحالية.');
      return;
    }
    setBusy(true);
    try {
      await changePassword(currentPassword, newPassword);
      navigate('/login', { replace: true, state: { passwordChanged: true } });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'تعذر تغيير كلمة المرور.');
    } finally {
      setBusy(false);
    }
  }

  function handleCancel() {
    signOut();
    navigate('/login', { replace: true });
  }

  return (
    <div className="login-page">
      <section className="login-visual">
        <div className="visual-orbit orbit-one" /><div className="visual-orbit orbit-two" />
        <div className="visual-content">
          <div className="visual-symbol"><KeyRound size={27} strokeWidth={1.8} /></div>
          <span className="visual-kicker">ACCOUNT · SECURITY · CONTROL</span>
          <h1>خطوة إلزامية.<br /><em>حساب أكثر أماناً.</em></h1>
          <p>يطلب المستشفى تغيير كلمة المرور المؤقتة قبل استخدام النظام. اختر كلمة مرور قوية وفريدة لا تستخدمها في أي خدمة أخرى.</p>
          <div className="visual-badges">
            <span><ShieldCheck size={13} /> سياسة كلمات مرور صارمة</span>
            <span><TimerReset size={13} /> إبطال فوري للجلسات</span>
          </div>
          <div className="visual-foot"><span /> حماية وصول متوافقة مع سياسة الأمان</div>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <Brand />
          <div className="login-intro">
            <span className="eyebrow">تغيير إلزامي لكلمة المرور</span>
            <h2>تعيين كلمة مرور جديدة</h2>
            <p>الحساب: <strong>{user?.username}</strong> — يجب تغيير كلمة المرور قبل المتابعة.</p>
          </div>
          <form onSubmit={handleSubmit} className="login-form">
            <label>كلمة المرور الحالية
              <input type="password" autoComplete="current-password" value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)} placeholder="أدخل كلمة المرور الحالية" required />
            </label>
            <label>كلمة المرور الجديدة
              <input type="password" autoComplete="new-password" value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)} placeholder="أدخل كلمة المرور الجديدة" required />
            </label>
            <label>تأكيد كلمة المرور الجديدة
              <input type="password" autoComplete="new-password" value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)} placeholder="أعد إدخال كلمة المرور الجديدة" required />
            </label>
            <p className="form-hint">{PASSWORD_RULE}</p>
            {error && <div className="alert alert-error" role="alert">{error}</div>}
            <button className="primary-button login-submit" disabled={busy}>
              {busy ? 'جارٍ حفظ كلمة المرور…' : 'حفظ كلمة المرور والمتابعة'} <span>←</span>
            </button>
            <button type="button" className="secondary-button" onClick={handleCancel} disabled={busy}>
              تسجيل الخروج
            </button>
          </form>
          <div className="login-security"><span><ShieldCheck size={14} /></span><span>بعد التغيير تُبطَل كل الجلسات ويُطلب تسجيل الدخول من جديد.</span></div>
        </div>
      </section>
    </div>
  );
}

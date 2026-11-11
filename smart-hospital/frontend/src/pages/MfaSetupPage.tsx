import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { KeyRound, ShieldCheck } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { homeForRoles } from '../auth/home';
import Brand from '../components/Brand';
import type { MfaEnrollmentConfirm, MfaEnrollmentStart } from '../types/api';

function groupedSecret(secret: string): string {
  return secret.replace(/(.{4})/g, '$1 ').trim();
}

/**
 * TOTP enrolment. Reached automatically when policy requires MFA, or voluntarily
 * from the security page. The secret is shown once; recovery codes are shown once
 * after confirmation and are never retrievable again.
 */
export default function MfaSetupPage() {
  const { token, user, completeMfaEnrollment, signOut } = useAuth();
  const navigate = useNavigate();
  const started = useRef(false);
  const [enrollment, setEnrollment] = useState<MfaEnrollmentStart | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [alreadyEnabled, setAlreadyEnabled] = useState(false);
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    void apiRequest<MfaEnrollmentStart>('/auth/mfa/enroll/start', token, { method: 'POST' })
      .then((result) => setEnrollment(result))
      .catch((reason) => {
        if (reason instanceof ApiError && (reason.status === 409 || reason.code === 'MFA_ALREADY_ENABLED')) {
          setAlreadyEnabled(true);
        } else {
          setError(reason instanceof Error ? reason.message : 'تعذر بدء تسجيل المصادقة الثنائية.');
        }
      })
      .finally(() => setBusy(false));
  }, [token]);

  async function handleConfirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    setError('');
    setBusy(true);
    try {
      const result = await apiRequest<MfaEnrollmentConfirm>('/auth/mfa/enroll/confirm', token, {
        method: 'POST',
        body: JSON.stringify({ code }),
      });
      setRecoveryCodes(result.recoveryCodes);
      setCode('');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'تعذر تأكيد الرمز.');
    } finally {
      setBusy(false);
    }
  }

  async function copySecret() {
    if (!enrollment) return;
    try {
      await navigator.clipboard.writeText(enrollment.secret);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2500);
    } catch {
      setError('تعذر النسخ التلقائي. انسخ المفتاح يدوياً.');
    }
  }

  function continueToApp() {
    completeMfaEnrollment();
    navigate(homeForRoles(user?.roleCodes ?? []), { replace: true });
  }

  function cancel() {
    signOut();
    navigate('/login', { replace: true });
  }

  return (
    <div className="login-page">
      <section className="login-visual">
        <div className="visual-orbit orbit-one" /><div className="visual-orbit orbit-two" />
        <div className="visual-content">
          <div className="visual-symbol"><KeyRound size={27} strokeWidth={1.8} /></div>
          <span className="visual-kicker">IDENTITY · TRUST · ACCESS</span>
          <h1>طبقة حماية إضافية.<br /><em>لكل وصول حساس.</em></h1>
          <p>تعتمد المصادقة الثنائية على شيء تعرفه (كلمة المرور) وشيء تملكه (تطبيق المصادقة). حتى لو سُرقت كلمة المرور وحدها، يبقى الحساب مغلقاً في وجه المهاجم.</p>
          <div className="visual-badges">
            <span><ShieldCheck size={13} /> TOTP بمعيار RFC 6238</span>
            <span><ShieldCheck size={13} /> تشفير AES-256-GCM</span>
            <span><ShieldCheck size={13} /> رموز استرداد لمرة واحدة</span>
          </div>
          <div className="visual-foot"><span /> TOTP بمعيار RFC 6238</div>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <Brand />
          {recoveryCodes ? (
            <>
              <div className="login-intro">
                <span className="eyebrow">تم تفعيل المصادقة الثنائية</span>
                <h2>احفظ رموز الاسترداد</h2>
                <p>هذه الرموز تُعرض مرة واحدة فقط. احفظها في مكان آمن منفصل عن جهاز المصادقة. كل رمز يعمل مرة واحدة.</p>
              </div>
              <div className="recovery-code-grid" dir="ltr">
                {recoveryCodes.map((recoveryCode) => <code key={recoveryCode}>{recoveryCode}</code>)}
              </div>
              <div className="alert alert-error" role="alert">إذا فقدت جهاز المصادقة ولم تملك رمز استرداد، فلن تتمكن من الدخول دون تدخل مسؤول الأمان.</div>
              <button className="primary-button login-submit" onClick={continueToApp}>لقد حفظت الرموز — متابعة <span>←</span></button>
            </>
          ) : alreadyEnabled ? (
            <>
              <div className="login-intro">
                <span className="eyebrow">الحساب محمي</span>
                <h2>المصادقة الثنائية مفعّلة مسبقاً</h2>
                <p>هذا الحساب يملك عاملاً ثانياً نشطاً. يمكنك إدارة الرموز أو تعطيل المصادقة من صفحة الأمان بعد التحقق.</p>
              </div>
              <Link className="primary-button login-submit" to="/security">إدارة إعدادات الأمان <span>←</span></Link>
              <button type="button" className="secondary-button" onClick={cancel}>تسجيل الخروج</button>
            </>
          ) : (
            <>
              <div className="login-intro">
                <span className="eyebrow">إعداد إلزامي / اختياري</span>
                <h2>تسجيل المصادقة الثنائية</h2>
                <p>افتح تطبيق مصادقة (Google Authenticator أو Microsoft Authenticator أو 1Password) وأضف الحساب يدوياً بالمفتاح التالي.</p>
              </div>
              {busy && !enrollment && <div className="panel"><div className="empty-state">جارٍ تجهيز مفتاح التشفير…</div></div>}
              {error && <div className="alert alert-error" role="alert">{error}</div>}
              {enrollment && (
                <>
                  <div className="mfa-secret-box">
                    <span>مفتاح TOTP للحساب <strong>{enrollment.account}</strong></span>
                    <code dir="ltr">{groupedSecret(enrollment.secret)}</code>
                    <small>النوع: {enrollment.algorithm} · {enrollment.digits} أرقام · {enrollment.period} ثانية</small>
                    <button type="button" className="secondary-button" onClick={copySecret}>{copied ? 'تم النسخ' : 'نسخ المفتاح'}</button>
                  </div>
                  <details className="mfa-uri">
                    <summary>إضافة عبر رابط otpauth</summary>
                    <code dir="ltr">{enrollment.otpauthUri}</code>
                  </details>
                  <form onSubmit={handleConfirm} className="login-form">
                    <label>رمز التحقق من التطبيق
                      <input
                        value={code}
                        onChange={(event) => setCode(event.target.value)}
                        placeholder="123456"
                        autoComplete="one-time-code"
                        inputMode="numeric"
                        maxLength={7}
                        required
                      />
                    </label>
                    {error && <div className="alert alert-error" role="alert">{error}</div>}
                    <button className="primary-button login-submit" disabled={busy}>
                      {busy ? 'جارٍ التحقق…' : 'تأكيد وتفعيل المصادقة'} <span>←</span>
                    </button>
                  </form>
                </>
              )}
              {!busy && !enrollment && error && (
                <button type="button" className="secondary-button" onClick={() => window.location.reload()}>إعادة المحاولة</button>
              )}
              <button type="button" className="secondary-button" onClick={cancel}>تسجيل الخروج</button>
            </>
          )}
          <div className="login-security"><span><ShieldCheck size={14} /></span><span>يُخزَّن السر مشفّراً بـAES-GCM، ولا يُعرض مرة أخرى بعد التفعيل.</span></div>
        </div>
      </section>
    </div>
  );
}

import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Fingerprint, HeartPulse, LockKeyhole, ShieldCheck } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { homeForRoles } from '../auth/home';
import Brand from '../components/Brand';
import type { LoginResult } from '../types/api';

function destinationFor(result: LoginResult): string {
  if (result.mustChangePassword && result.passwordRotationEnforced) return '/change-password';
  if (result.mfaEnrollmentRequired || result.user?.mfaEnrollmentRequired) return '/mfa-setup';
  return homeForRoles(result.user?.roleCodes ?? []);
}

export default function LoginPage() {
  const { signIn, verifyMfa } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const passwordChanged = Boolean((location.state as { passwordChanged?: boolean } | null)?.passwordChanged);
  const mfaDisabled = Boolean((location.state as { mfaDisabled?: boolean } | null)?.mfaDisabled);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [challengeToken, setChallengeToken] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const result = await signIn(username, password);
      if (result.mfaRequired) {
        if (!result.mfaChallengeToken) throw new Error('تعذر بدء التحقق الثنائي. أعد المحاولة.');
        setChallengeToken(result.mfaChallengeToken);
        // The password is no longer needed in component state after the server verifies it.
        setPassword('');
        setCode('');
        return;
      }
      navigate(destinationFor(result), { replace: true });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'تعذر تسجيل الدخول.');
    } finally {
      setBusy(false);
    }
  }

  async function handleVerify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const result = await verifyMfa(challengeToken, code);
      navigate(destinationFor(result), { replace: true });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'تعذر التحقق من الرمز.');
    } finally {
      setBusy(false);
    }
  }

  function restartLogin() {
    setChallengeToken('');
    setCode('');
    setError('');
    setPassword('');
  }

  return (
    <div className="login-page">
      <section className="login-visual">
        <div className="visual-orbit orbit-one" /><div className="visual-orbit orbit-two" />
        <div className="visual-content">
          <div className="visual-symbol"><HeartPulse size={28} strokeWidth={1.8} /></div>
          <span className="visual-kicker">CARE · CONNECT · INSIGHT</span>
          <h1>رعاية مترابطة.<br /><em>رؤية أوضح.</em></h1>
          <p>منصة موحدة تساعد فرق المستشفى على تنسيق العمل ومتابعة العمليات واتخاذ قرارات تشغيلية مبنية على البيانات.</p>
          <div className="visual-badges">
            <span><ShieldCheck size={13} /> تحقق من الصلاحيات</span>
            <span><LockKeyhole size={13} /> تشفير البيانات</span>
            <span><Fingerprint size={13} /> مصادقة ثنائية</span>
          </div>
          <div className="visual-foot"><span /> منظومة مؤسسية للرعاية الصحية</div>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <Brand />
          {challengeToken ? (
            <>
              <div className="login-intro">
                <span className="eyebrow">خطوة التحقق الثانية</span>
                <h2>المصادقة الثنائية</h2>
                <p>أدخل الرمز المكوّن من ستة أرقام من تطبيق المصادقة، أو أحد رموز الاسترداد الاحتياطية.</p>
              </div>
              <form onSubmit={handleVerify} className="login-form">
                <label>رمز المصادقة أو الاسترداد
                  <input
                    value={code}
                    onChange={(event) => setCode(event.target.value)}
                    placeholder="123456 أو ABCDE-FGHIJ"
                    autoComplete="one-time-code"
                    autoFocus
                    required
                  />
                </label>
                {error && <div className="alert alert-error" role="alert">{error}</div>}
                <button className="primary-button login-submit" disabled={busy}>
                  {busy ? 'جارٍ التحقق…' : 'تأكيد الرمز والمتابعة'} <span>←</span>
                </button>
                <button type="button" className="secondary-button" onClick={restartLogin} disabled={busy}>
                  العودة إلى بيانات الدخول
                </button>
              </form>
              <div className="login-security"><span><ShieldCheck size={14} /></span><span>تنتهي صلاحية جلسة التحقق بعد خمس دقائق، وبعد خمس محاولات خاطئة.</span></div>
            </>
          ) : (
            <>
              <div className="login-intro"><span className="eyebrow">مرحباً بعودتك</span><h2>تسجيل الدخول</h2><p>أدخل بيانات حساب المستشفى للمتابعة إلى مساحة العمل.</p></div>
              <form onSubmit={handleSubmit} className="login-form">
                {passwordChanged && <div className="alert alert-success" role="status">تم تغيير كلمة المرور بنجاح. سجّل الدخول بكلمة المرور الجديدة.</div>}
                {mfaDisabled && <div className="alert alert-success" role="status">تم تعطيل المصادقة الثنائية. سجّل الدخول من جديد.</div>}
                <label>اسم المستخدم<input autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="أدخل اسم المستخدم" required /></label>
                <label>كلمة المرور<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="أدخل كلمة المرور" required /></label>
                {error && <div className="alert alert-error" role="alert">{error}</div>}
                <button className="primary-button login-submit" disabled={busy}>{busy ? 'جارٍ التحقق…' : 'الدخول إلى النظام'} <span>←</span></button>
              </form>
              <div className="login-security"><span><ShieldCheck size={14} /></span><span>تُتحقق الهوية والصلاحيات من خادم المستشفى.</span></div>
              <small className="demo-warning">بيئة أكاديمية تجريبية — استخدم حسابات وبيانات اصطناعية فقط.</small>
            </>
          )}
        </div>
      </section>
    </div>
  );
}

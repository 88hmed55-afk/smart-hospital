import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { apiRequest } from '../api/client';
import type { CurrentSession, LoginResult } from '../types/api';

interface AuthContextValue {
  token: string | null;
  user: CurrentSession | null;
  mustChangePassword: boolean;
  mfaEnrollmentRequired: boolean;
  signIn: (username: string, password: string) => Promise<LoginResult>;
  verifyMfa: (challengeToken: string, code: string) => Promise<LoginResult>;
  signOut: () => void;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  completeMfaEnrollment: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function requireSession(result: LoginResult): CurrentSession {
  const session = result.user;
  if (!result.token || !session?.userId || !session.roleCodes?.length || !session.branchIds?.length) {
    throw new Error('الحساب غير مرتبط بأدوار وفروع نشطة في المستشفى.');
  }
  return session;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<CurrentSession | null>(null);
  const [mustChangePassword, setMustChangePassword] = useState(false);
  const [mfaEnrollmentRequired, setMfaEnrollmentRequired] = useState(false);

  const value = useMemo<AuthContextValue>(() => {
    function applySession(result: LoginResult) {
      const session = requireSession(result);
      // The bearer token is intentionally held in memory only; never persist it in browser storage.
      setToken(`Bearer ${result.token}`);
      setUser(session);
      setMustChangePassword(Boolean(result.mustChangePassword && result.passwordRotationEnforced));
      setMfaEnrollmentRequired(Boolean(result.mfaEnrollmentRequired || session.mfaEnrollmentRequired));
    }

    return {
      token,
      user,
      mustChangePassword,
      mfaEnrollmentRequired,
      async signIn(username, password) {
        const normalizedUsername = username.trim();
        if (!normalizedUsername || !password) throw new Error('أدخل اسم المستخدم وكلمة المرور.');
        const result = await apiRequest<LoginResult>('/auth/login', '', {
          method: 'POST',
          body: JSON.stringify({ username: normalizedUsername, password }),
        });
        if (result.mfaRequired) {
          if (!result.mfaChallengeToken) throw new Error('تعذر بدء التحقق الثنائي. أعد المحاولة.');
          // No session yet: the password step alone does not grant access.
          return result;
        }
        applySession(result);
        return result;
      },
      async verifyMfa(challengeToken, code) {
        const normalizedCode = code.trim();
        if (!normalizedCode) throw new Error('أدخل رمز المصادقة أو رمز الاسترداد.');
        const result = await apiRequest<LoginResult>('/auth/mfa/verify', '', {
          method: 'POST',
          body: JSON.stringify({ challengeToken, code: normalizedCode }),
        });
        applySession(result);
        return result;
      },
      signOut() {
        const current = token;
        setToken(null);
        setUser(null);
        setMustChangePassword(false);
        setMfaEnrollmentRequired(false);
        if (current) {
          void apiRequest('/auth/logout', current, { method: 'POST' }).catch(() => { /* session already gone */ });
        }
      },
      async changePassword(currentPassword, newPassword) {
        if (!token) throw new Error('انتهت الجلسة. سجّل الدخول من جديد.');
        await apiRequest<void>('/auth/password', token, {
          method: 'POST',
          body: JSON.stringify({ currentPassword, newPassword }),
        });
        // The server revokes every session on a successful rotation, including this one.
        setToken(null);
        setUser(null);
        setMustChangePassword(false);
        setMfaEnrollmentRequired(false);
      },
      completeMfaEnrollment() {
        setMfaEnrollmentRequired(false);
        setUser((current) => current ? { ...current, mfaEnabled: true, mfaEnrollmentRequired: false } : current);
      },
    };
  }, [token, user, mustChangePassword, mfaEnrollmentRequired]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}

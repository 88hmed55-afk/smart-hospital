import { Navigate, Outlet, Route, Routes, useOutletContext } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { homeForRoles } from './auth/home';
import HospitalLayout, { type LayoutOutletContext } from './layouts/HospitalLayout';
import DashboardPage from './pages/DashboardPage';
import LoginPage from './pages/LoginPage';
import PatientsPage from './pages/PatientsPage';
import AppointmentsPage from './pages/AppointmentsPage';
import ClinicalPage from './pages/ClinicalPage';
import LaboratoryPage from './pages/LaboratoryPage';
import PharmacyPage from './pages/PharmacyPage';
import AdmissionsPage from './pages/AdmissionsPage';
import BillingPage from './pages/BillingPage';
import AnalyticsPage from './pages/AnalyticsPage';
import InventoryPage from './pages/InventoryPage';
import AccountingPage from './pages/AccountingPage';
import AdministrationPage from './pages/AdministrationPage';
import SettingsPage from './pages/SettingsPage';
import AuditLogsPage from './pages/AuditLogsPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import MfaSetupPage from './pages/MfaSetupPage';
import SecurityPage from './pages/SecurityPage';

function RequireAuth() {
  const { token, mustChangePassword, mfaEnrollmentRequired } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  if (mustChangePassword) return <Navigate to="/change-password" replace />;
  if (mfaEnrollmentRequired) return <Navigate to="/mfa-setup" replace />;
  return <Outlet />;
}
function ChangePasswordOnly() {
  const { token, user, mustChangePassword, mfaEnrollmentRequired } = useAuth();
  if (!token || !user) return <Navigate to="/login" replace />;
  if (!mustChangePassword) {
    return <Navigate to={mfaEnrollmentRequired ? '/mfa-setup' : homeForRoles(user.roleCodes)} replace />;
  }
  return <ChangePasswordPage />;
}
function MfaSetupOnly() {
  const { token, user, mustChangePassword } = useAuth();
  if (!token || !user) return <Navigate to="/login" replace />;
  if (mustChangePassword) return <Navigate to="/change-password" replace />;
  return <MfaSetupPage />;
}
function RequireRoles({ roles }: { roles: string[] }) {
  const { token, user } = useAuth();
  // Pathless routes sit between HospitalLayout and the page, so the layout context
  // (branchId/branch) must be read and re-provided explicitly; otherwise the nested
  // <Outlet /> renders without context and pages such as SettingsPage crash.
  const layoutContext = useOutletContext<LayoutOutletContext | undefined>();
  if (!token) return <Navigate to="/login" replace />;
  return user?.roleCodes.some((role) => roles.includes(role))
    ? <Outlet context={layoutContext} />
    : <Navigate to={homeForRoles(user?.roleCodes ?? [])} replace />;
}
function LoginOnly() {
  const { token, user, mustChangePassword, mfaEnrollmentRequired } = useAuth();
  if (token && user) {
    return <Navigate to={mustChangePassword ? '/change-password'
      : mfaEnrollmentRequired ? '/mfa-setup'
      : homeForRoles(user.roleCodes)} replace />;
  }
  return <LoginPage />;
}
function DefaultRoute() {
  const { token, user, mustChangePassword, mfaEnrollmentRequired } = useAuth();
  if (!token || !user) return <Navigate to="/login" replace />;
  return <Navigate to={mustChangePassword ? '/change-password'
    : mfaEnrollmentRequired ? '/mfa-setup'
    : homeForRoles(user.roleCodes)} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginOnly />} />
      <Route path="/change-password" element={<ChangePasswordOnly />} />
      <Route path="/mfa-setup" element={<MfaSetupOnly />} />
      <Route element={<RequireAuth />}>
        <Route element={<HospitalLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/patients" element={<PatientsPage />} />
          <Route path="/appointments" element={<AppointmentsPage />} />
          <Route path="/clinical" element={<ClinicalPage />} />
          <Route path="/laboratory" element={<LaboratoryPage />} />
          <Route path="/pharmacy" element={<PharmacyPage />} />
          <Route path="/admissions" element={<AdmissionsPage />} />
          <Route path="/billing" element={<BillingPage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/inventory" element={<InventoryPage />} />
          <Route path="/accounting" element={<AccountingPage />} />
          <Route path="/administration" element={<AdministrationPage />} />
          <Route path="/security" element={<SecurityPage />} />
          <Route element={<RequireRoles roles={['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER']} />}>
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/audit" element={<AuditLogsPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<DefaultRoute />} />
    </Routes>
  );
}

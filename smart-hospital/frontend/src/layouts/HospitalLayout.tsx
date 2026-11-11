import { useEffect, useMemo, useState, type ComponentType } from 'react';
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Activity,
  BedDouble,
  BookOpen,
  Boxes,
  Building2,
  CalendarDays,
  ChevronDown,
  FlaskConical,
  KeyRound,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  Pill,
  ReceiptText,
  Search,
  Settings,
  ShieldCheck,
  Stethoscope,
  Sun,
  TrendingUp,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { canAccessPage, pageRoles } from '../auth/pageAccess';
import { homeForRoles } from '../auth/home';
import Brand from '../components/Brand';
import NotificationBell from '../components/NotificationBell';
import type { Branch } from '../types/api';

export interface LayoutOutletContext {
  branchId: string;
  branch: Branch | undefined;
}

type NavItem = { label: string; path: string; icon: LucideIcon; roles: string[] };
const navigation: { title: string; items: NavItem[] }[] = [
  {
    title: 'الرئيسية',
    items: [
      { label: 'لوحة القيادة', path: '/dashboard', icon: LayoutDashboard, roles: pageRoles['/dashboard'] },
      { label: 'الأمان', path: '/security', icon: ShieldCheck, roles: pageRoles['/security'] },
    ],
  },
  {
    title: 'الرعاية الصحية',
    items: [
      { label: 'المرضى والملفات', path: '/patients', icon: Users, roles: pageRoles['/patients'] },
      { label: 'المواعيد والاستقبال', path: '/appointments', icon: CalendarDays, roles: pageRoles['/appointments'] },
      { label: 'الزيارات الطبية', path: '/clinical', icon: Stethoscope, roles: pageRoles['/clinical'] },
      { label: 'المختبر', path: '/laboratory', icon: FlaskConical, roles: pageRoles['/laboratory'] },
      { label: 'الصيدلية', path: '/pharmacy', icon: Pill, roles: pageRoles['/pharmacy'] },
      { label: 'التنويم والأسرة', path: '/admissions', icon: BedDouble, roles: pageRoles['/admissions'] },
    ],
  },
  {
    title: 'الإدارة والتشغيل',
    items: [
      { label: 'الفواتير والمدفوعات', path: '/billing', icon: ReceiptText, roles: pageRoles['/billing'] },
      { label: 'المحاسبة العامة', path: '/accounting', icon: BookOpen, roles: pageRoles['/accounting'] },
      { label: 'المخزون والمشتريات', path: '/inventory', icon: Boxes, roles: pageRoles['/inventory'] },
      { label: 'التحليلات الذكية', path: '/analytics', icon: TrendingUp, roles: pageRoles['/analytics'] },
      { label: 'إدارة النظام', path: '/administration', icon: Settings, roles: pageRoles['/administration'] },
      { label: 'إعدادات المستشفى', path: '/settings', icon: Activity, roles: pageRoles['/settings'] },
      { label: 'سجل التدقيق', path: '/audit', icon: ShieldCheck, roles: pageRoles['/audit'] },
    ],
  },
];

const roleTitles: Record<string, string> = {
  HOSPITAL_ADMIN: 'مدير النظام',
  HOSPITAL_MANAGER: 'مدير المستشفى',
  DOCTOR: 'طبيب',
  NURSE: 'كادر تمريض',
  RECEPTIONIST: 'موظف استقبال',
  LAB_TECHNICIAN: 'فني مختبر',
  LAB_SUPERVISOR: 'مشرف مختبر',
  PHARMACIST: 'صيدلي',
  ACCOUNTANT: 'محاسب',
  INVENTORY_OFFICER: 'مسؤول مخزون',
};

const iconMap: Record<string, LucideIcon> = {
  '/dashboard': LayoutDashboard,
  '/security': ShieldCheck,
  '/patients': Users,
  '/appointments': CalendarDays,
  '/clinical': Stethoscope,
  '/laboratory': FlaskConical,
  '/pharmacy': Pill,
  '/admissions': BedDouble,
  '/billing': ReceiptText,
  '/accounting': BookOpen,
  '/inventory': Boxes,
  '/analytics': TrendingUp,
  '/administration': Settings,
  '/settings': Activity,
  '/audit': ShieldCheck,
};

function readTheme(): 'light' | 'dark' {
  try {
    const stored = localStorage.getItem('sh-theme');
    if (stored === 'dark' || stored === 'light') return stored;
  } catch {
    /* storage may be disabled */
  }
  return 'light';
}

export default function HospitalLayout() {
  const { token, user, signOut } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [branchId, setBranchId] = useState('');
  const [mobileOpen, setMobileOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [paletteQuery, setPaletteQuery] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [theme, setTheme] = useState<'light' | 'dark'>(readTheme);

  const branchesQuery = useQuery({
    queryKey: ['branches', token],
    queryFn: () => apiRequest<Branch[]>('/organization/branches', token!),
    enabled: Boolean(token),
  });
  const branches = branchesQuery.data ?? [];

  useEffect(() => {
    if (!branchId && branches.length) {
      const preferred = branches.find((branch) => branch.id === user?.defaultBranchId) ?? branches[0];
      if (preferred) setBranchId(preferred.id);
    }
  }, [branches, branchId, user?.defaultBranchId]);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem('sh-theme', theme);
    } catch {
      /* ignore */
    }
  }, [theme]);

  useEffect(() => {
    setMobileOpen(false);
    setMenuOpen(false);
    setPaletteOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPaletteQuery('');
        setPaletteOpen((value) => !value);
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  const visibleGroups = useMemo(
    () =>
      navigation
        .map((group) => ({
          ...group,
          items: group.items.filter((item) => item.roles.some((role) => user?.roleCodes.includes(role))),
        }))
        .filter((group) => group.items.length > 0),
    [user?.roleCodes],
  );

  const paletteItems = useMemo(() => visibleGroups.flatMap((group) => group.items), [visibleGroups]);
  const filteredPalette = useMemo(() => {
    const needle = paletteQuery.trim();
    if (!needle) return paletteItems;
    return paletteItems.filter((item) => item.label.includes(needle));
  }, [paletteItems, paletteQuery]);

  const activeBranch = branches.find((branch) => branch.id === branchId);
  const currentItem = paletteItems.find((item) => item.path === location.pathname);
  const currentIcon = iconMap[location.pathname] ?? LayoutDashboard;
  const CurrentIcon: ComponentType<{ size?: number; strokeWidth?: number; className?: string }> = currentIcon;
  const roleTitle = user?.roleCodes.map((code) => roleTitles[code]).filter(Boolean)[0] ?? 'مستخدم مصرّح';
  const logout = () => {
    signOut();
    navigate('/login', { replace: true });
  };

  // Direct URL visits to a module the API would reject are bounced to the role's
  // home screen instead of rendering a page that can only produce 403s.
  if (!canAccessPage(location.pathname, user?.roleCodes)) {
    return <Navigate to={homeForRoles(user?.roleCodes ?? [])} replace />;
  }

  function go(path: string) {
    setPaletteOpen(false);
    setPaletteQuery('');
    navigate(path);
  }

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileOpen ? 'sidebar-open' : ''}`}>
        <div className="sidebar-brand">
          <Brand />
        </div>
        <div className="sidebar-hospital">
          <span className="online-dot" />
          <span>بيئة المستشفى</span>
          <small>نظام تشغيل</small>
        </div>
        <nav className="side-nav" aria-label="القائمة الرئيسية">
          {visibleGroups.map((group) => (
            <section className="nav-group" key={group.title}>
              <h2>{group.title}</h2>
              {group.items.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.path}
                    to={item.path}
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => `nav-link ${isActive ? 'nav-link-active' : ''}`}
                  >
                    <span className="nav-mark">
                      <Icon size={16} strokeWidth={1.8} />
                    </span>
                    <span>{item.label}</span>
                  </NavLink>
                );
              })}
            </section>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="security-note">
            <span>
              <ShieldCheck size={15} strokeWidth={1.8} />
            </span>
            <div>
              <strong>وصول محمي</strong>
              <small>الصلاحيات تُفرض من الخادم</small>
            </div>
          </div>
          <button className="logout-button" onClick={logout}>
            <LogOut size={15} strokeWidth={1.8} /> تسجيل الخروج
          </button>
          <small className="version-label">Smart Hospital · v0.18</small>
        </div>
      </aside>

      {mobileOpen && <button className="mobile-scrim" aria-label="إغلاق القائمة" onClick={() => setMobileOpen(false)} />}

      <main className="main-area">
        <header className="topbar">
          <button className="mobile-menu-button" onClick={() => setMobileOpen(true)} aria-label="فتح القائمة">
            <Menu size={19} />
          </button>
          <div className="topbar-heading">
            <span className="eyebrow">مساحة العمل</span>
            <strong>
              <CurrentIcon size={15} strokeWidth={1.9} /> {currentItem?.label ?? 'Smart Hospital'}
            </strong>
          </div>

          <button className="topbar-search" onClick={() => setPaletteOpen(true)}>
            <Search size={15} strokeWidth={1.9} />
            <span>ابحث عن صفحة أو إجراء…</span>
            <kbd>Ctrl K</kbd>
          </button>

          <div className="topbar-actions">
            <label className="branch-switcher">
              <span>
                <Building2 size={14} strokeWidth={1.8} />
              </span>
              <select value={branchId} onChange={(event) => setBranchId(event.target.value)} disabled={!branches.length}>
                {branches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </select>
            </label>
            <div className="topbar-divider" />
            <button
              className="theme-toggle"
              onClick={() => setTheme((value) => (value === 'dark' ? 'light' : 'dark'))}
              aria-label={theme === 'dark' ? 'التبديل إلى الوضع الفاتح' : 'التبديل إلى الوضع الداكن'}
              title={theme === 'dark' ? 'الوضع الفاتح' : 'الوضع الداكن'}
            >
              {theme === 'dark' ? <Sun size={17} strokeWidth={1.8} /> : <Moon size={17} strokeWidth={1.8} />}
            </button>
            <NotificationBell />
            <div className="user-anchor">
              <button
                className="user-chip"
                onClick={() => setMenuOpen((value) => !value)}
                aria-expanded={menuOpen}
                aria-haspopup="menu"
              >
                <div className="avatar">{user?.username.slice(0, 1).toUpperCase()}</div>
                <div>
                  <strong>{user?.username}</strong>
                  <small>{roleTitle}</small>
                </div>
                <ChevronDown size={13} className="user-chevron" />
              </button>
              {menuOpen && (
                <>
                  <button className="menu-scrim" aria-label="إغلاق قائمة المستخدم" onClick={() => setMenuOpen(false)} />
                  <div className="user-menu" role="menu">
                    <div className="user-menu-head">
                      <div className="avatar">{user?.username.slice(0, 1).toUpperCase()}</div>
                      <div>
                        <strong>{user?.username}</strong>
                        <small>{roleTitle}</small>
                      </div>
                    </div>
                    <button className="user-menu-link" onClick={() => { setMenuOpen(false); navigate('/security'); }}>
                      <KeyRound size={15} /> الأمان والمصادقة الثنائية
                    </button>
                    <button
                      className="user-menu-link"
                      onClick={() => { setMenuOpen(false); setPaletteQuery(''); setPaletteOpen(true); }}
                    >
                      <Search size={15} /> البحث السريع
                    </button>
                    <button className="user-menu-link danger" onClick={logout}>
                      <LogOut size={15} /> تسجيل الخروج
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </header>

        <div className="page-wrap">
          {branchesQuery.isError && (
            <div className="alert alert-error">
              تعذر تحميل الفروع المصرح بها. أعد تسجيل الدخول أو راجع ربط حسابك بالفروع.
            </div>
          )}
          <Outlet context={{ branchId, branch: activeBranch } satisfies LayoutOutletContext} />
        </div>
      </main>

      {paletteOpen && (
        <div className="palette-backdrop" onClick={() => setPaletteOpen(false)}>
          <div className="palette" onClick={(event) => event.stopPropagation()}>
            <div className="palette-input">
              <Search size={17} />
              <input
                autoFocus
                value={paletteQuery}
                onChange={(event) => setPaletteQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && filteredPalette[0]) go(filteredPalette[0].path);
                  if (event.key === 'Escape') setPaletteOpen(false);
                }}
                placeholder="اكتب اسم الصفحة…"
                aria-label="البحث في مساحة العمل"
              />
              <kbd>ESC</kbd>
            </div>
            <div className="palette-list">
              {filteredPalette.length === 0 && <div className="palette-empty">لا توجد صفحة مطابقة لبحثك.</div>}
              {filteredPalette.map((item) => {
                const Icon = item.icon;
                return (
                  <button key={item.path} className="palette-item" onClick={() => go(item.path)}>
                    <span>
                      <Icon size={15} strokeWidth={1.8} />
                    </span>
                    {item.label}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

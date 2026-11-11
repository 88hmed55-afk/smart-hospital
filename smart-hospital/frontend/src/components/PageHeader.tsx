import type { ReactNode } from 'react';
import { CalendarDays, Moon, Sparkles, Sun, type LucideIcon } from 'lucide-react';
import { useOutletContext } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

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

function greetingFor(hour: number): { text: string; night: boolean } {
  if (hour >= 5 && hour < 12) return { text: 'صباح الخير', night: false };
  if (hour >= 12 && hour < 17) return { text: 'طاب يومك', night: false };
  return { text: 'مساء الخير', night: true };
}

export interface PageHeaderProps {
  eyebrow: string;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  icon?: LucideIcon;
  meta?: string[];
}

/**
 * The signature screen header: a dark aurora panel with a time-aware greeting,
 * branch/date context and the page's primary actions. It reads the active branch
 * from the layout outlet so every module receives the same context for free.
 */
export default function PageHeader({ eyebrow, title, description, actions, icon: Icon, meta = [] }: PageHeaderProps) {
  const { user } = useAuth();
  const layout = useOutletContext<LayoutOutletContext | undefined>();
  const greeting = greetingFor(new Date().getHours());
  const role = user?.roleCodes.map((code) => roleTitles[code]).filter(Boolean)[0] ?? 'مستخدم مصرّح';
  const today = new Intl.DateTimeFormat('ar-YE', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
  const chips = Array.from(new Set([layout?.branch?.name ?? 'كل الفروع', ...meta].filter(Boolean)));

  return (
    <header className="hero">
      {Icon && <Icon className="hero-watermark" size={168} strokeWidth={0.9} aria-hidden="true" />}
      <div className="hero-top">
        <span className="hero-eyebrow">
          <Sparkles size={12} aria-hidden="true" />
          {eyebrow}
        </span>
        <div className="hero-meta">
          {chips.map((chip) => (
            <span key={chip}>{chip}</span>
          ))}
          <span>
            <CalendarDays size={11} aria-hidden="true" />
            {today}
          </span>
        </div>
      </div>
      <div className="hero-body">
        <div className="hero-copy">
          <p className="hero-greeting">
            {greeting.night ? <Moon size={13} aria-hidden="true" /> : <Sun size={13} aria-hidden="true" />}
            {greeting.text}، <strong>{user?.username ?? 'بك'}</strong>
            <span className="hero-spark" aria-hidden="true">✦</span>
            <span className="hero-role">{role}</span>
          </p>
          <h1 className="hero-title">{title}</h1>
          {description && <p className="hero-description">{description}</p>}
        </div>
        {actions && <div className="hero-actions">{actions}</div>}
      </div>
    </header>
  );
}

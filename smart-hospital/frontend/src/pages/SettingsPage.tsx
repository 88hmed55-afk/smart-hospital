import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useOutletContext } from 'react-router-dom';
import { Settings2 } from 'lucide-react';
import { ApiError, apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import PageHeader from '../components/PageHeader';
import ExportMenu from '../components/ExportMenu';
import type { ExportColumn } from '../lib/export';
import type { SystemSetting } from '../types/api';
import type { LayoutOutletContext } from '../layouts/HospitalLayout';

const errorText = (error: unknown) => error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'تعذر تنفيذ العملية.';
const numberText = (value: number) => Number(value).toLocaleString('ar-YE', { maximumFractionDigits: 2 });
const sourceLabel: Record<SystemSetting['effectiveSource'], string> = { BRANCH: 'قيمة مخصصة للفرع', GLOBAL: 'قيمة عامة', DEFAULT: 'القيمة الافتراضية' };

const settingExportColumns: ExportColumn<SystemSetting>[] = [
  { header: 'الإعداد', value: (row) => row.label, width: 1.6 },
  { header: 'المفتاح', value: (row) => row.key, width: 1.5, align: 'left' },
  { header: 'الوحدة', value: (row) => row.unit, width: 0.6, align: 'center' },
  { header: 'القيمة الفعالة', value: (row) => Number(row.effectiveValue), width: 0.9 },
  { header: 'قيمة الفرع', value: (row) => (row.branchValue == null ? '—' : Number(row.branchValue)), width: 0.9 },
  { header: 'القيمة العامة', value: (row) => (row.globalValue == null ? '—' : Number(row.globalValue)), width: 0.9 },
  { header: 'الافتراضي', value: (row) => Number(row.defaultValue), width: 0.9 },
  { header: 'النطاق', value: (row) => `${row.minimum} – ${row.maximum}`, width: 0.9, align: 'center' },
  { header: 'المصدر', value: (row) => sourceLabel[row.effectiveSource], width: 1.1 },
];

export default function SettingsPage() {
  const { token, user } = useAuth();
  const { branchId, branch } = useOutletContext<LayoutOutletContext>();
  const queryClient = useQueryClient();
  const isAdmin = user?.roleCodes.includes('HOSPITAL_ADMIN') ?? false;
  const [scope, setScope] = useState<'BRANCH' | 'GLOBAL'>('BRANCH');
  const [values, setValues] = useState<Record<string, string>>({});
  const [savedMessage, setSavedMessage] = useState('');

  const query = useQuery({
    queryKey: ['system-settings', branchId],
    queryFn: () => apiRequest<SystemSetting[]>(`/system/settings?branchId=${branchId}`, token!),
    enabled: Boolean(token && branchId),
  });

  useEffect(() => {
    if (query.data) {
      setValues((previous) => {
        const next = { ...previous };
        for (const setting of query.data ?? []) {
          const branchDraftKey = `BRANCH:${branchId}:${setting.key}`;
          const globalDraftKey = `GLOBAL:${setting.key}`;
          if (!(branchDraftKey in next)) next[branchDraftKey] = String(setting.branchValue ?? setting.effectiveValue);
          if (!(globalDraftKey in next)) next[globalDraftKey] = String(setting.globalValue ?? setting.defaultValue);
        }
        return next;
      });
    }
  }, [query.data, branchId]);

  const update = useMutation({
    mutationFn: ({ setting, value }: { setting: SystemSetting; value: number }) => apiRequest<SystemSetting[]>(
      '/system/settings', token!, {
        method: 'PUT',
        body: JSON.stringify({
          key: setting.key,
          branchId: scope === 'GLOBAL' ? null : branchId,
          value,
        }),
      }),
    onSuccess: async (_result, variables) => {
      setSavedMessage(`تم حفظ إعداد «${variables.setting.label}».`);
      await queryClient.invalidateQueries({ queryKey: ['system-settings'] });
      await queryClient.invalidateQueries({ queryKey: ['analytics-dashboard'] });
      await queryClient.invalidateQueries({ queryKey: ['decision-cards'] });
    },
  });

  const visibleSettings = useMemo(() => query.data ?? [], [query.data]);
  function draftKey(setting: SystemSetting) { return scope === 'GLOBAL' ? `GLOBAL:${setting.key}` : `BRANCH:${branchId}:${setting.key}`; }
  function draftValue(setting: SystemSetting) {
    const key = draftKey(setting);
    const fallback = scope === 'GLOBAL' ? setting.globalValue ?? setting.defaultValue : setting.branchValue ?? setting.effectiveValue;
    return values[key] ?? String(fallback);
  }
  function validValue(setting: SystemSetting) {
    const value = Number(draftValue(setting));
    return Number.isFinite(value) && value >= setting.minimum && value <= setting.maximum;
  }

  return <div className="module-page governance-page">
    <PageHeader
      eyebrow="SYSTEM CONFIGURATION"
      title="إعدادات المستشفى"
      description="إدارة الحدود التشغيلية المستخدمة في التحليلات والتنبيهات، دون تعديل قواعد العمل الطبية."
      icon={Settings2}
      meta={[scope === 'GLOBAL' ? 'إعداد عام' : branch?.name ?? 'الفرع الحالي']}
      actions={
        <>
          <ExportMenu
            fileName="إعدادات-المستشفى"
            title="تقرير إعدادات المستشفى"
            subtitle={`الحدود التشغيلية — ${scope === 'GLOBAL' ? 'عام لكل الفروع' : branch?.name ?? 'الفرع الحالي'}`}
            columns={settingExportColumns}
            rows={visibleSettings}
          />
          {isAdmin && <label className="governance-scope">نطاق الإعداد
            <select value={scope} onChange={(event) => { setScope(event.target.value as 'BRANCH' | 'GLOBAL'); setSavedMessage(''); }}>
              <option value="BRANCH">الفرع الحالي</option><option value="GLOBAL">عام لكل الفروع</option>
            </select>
          </label>}
        </>
      }
    />
    <div className="governance-context"><strong>{scope === 'GLOBAL' ? 'إعداد عام' : branch?.name ?? 'الفرع الحالي'}</strong><span>{scope === 'GLOBAL' ? 'تؤثر هذه القيمة على الفروع التي لا تملك تجاوزاً محلياً.' : 'قيمة الفرع تتقدم على القيمة العامة عند حساب المؤشرات.'}</span></div>
    {query.isError && <div className="alert alert-error">{errorText(query.error)}</div>}
    {update.isError && <div className="alert alert-error">{errorText(update.error)}</div>}
    {savedMessage && <div className="alert alert-success">{savedMessage}</div>}
    {query.isLoading ? <div className="panel"><div className="empty-state">جارٍ تحميل الإعدادات…</div></div> : <div className="settings-grid">
      {visibleSettings.map((setting) => {
        const inputValue = scope === 'GLOBAL'
          ? setting.globalValue ?? setting.defaultValue
          : setting.branchValue ?? setting.effectiveValue;
        const currentText = scope === 'GLOBAL' ? setting.globalValue : setting.branchValue;
        const keyForDraft = draftKey(setting);
        const valueForDraft = draftValue(setting);
        return <article className="panel setting-card" key={setting.key}>
          <div className="setting-card-header"><div><h2>{setting.label}</h2><code dir="ltr">{setting.key}</code></div><span className={`setting-source setting-source-${setting.effectiveSource.toLowerCase()}`}>{sourceLabel[setting.effectiveSource]}</span></div>
          <p>{setting.description}</p>
          <div className="setting-current"><span>القيمة الفعالة حالياً</span><strong>{numberText(setting.effectiveValue)} {setting.unit}</strong></div>
          <div className="setting-value-row">
            <label>القيمة الجديدة
              <div className="setting-input-wrap"><input inputMode="decimal" value={valueForDraft || String(inputValue)} onChange={(event) => { setValues((previous) => ({ ...previous, [keyForDraft]: event.target.value })); setSavedMessage(''); }} /><span>{setting.unit}</span></div>
            </label>
            <button className="primary-button" disabled={!validValue(setting) || update.isPending || Number(valueForDraft) === Number(inputValue)} onClick={() => update.mutate({ setting, value: Number(valueForDraft) })}>{update.isPending ? 'جارٍ الحفظ…' : 'حفظ الإعداد'}</button>
          </div>
          <div className="setting-footer"><span>النطاق المسموح: {numberText(setting.minimum)} – {numberText(setting.maximum)} {setting.unit}</span><span>{scope === 'GLOBAL' ? 'عام' : currentText == null ? 'لا يوجد تجاوز محلي' : 'يوجد تجاوز محلي'}</span></div>
        </article>;
      })}
    </div>}
    <p className="governance-note">التغييرات تسجل في سجل التدقيق. لا تسمح هذه الإعدادات للنظام بإصدار تشخيص أو توصية علاجية؛ فهي حدود تشغيلية قابلة للتفسير فقط.</p>
  </div>;
}

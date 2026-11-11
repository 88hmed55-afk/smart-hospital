import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronDown, Download, FileSpreadsheet, FileText, Loader2 } from 'lucide-react';
import {
  exportExcel,
  exportPdf,
  type ExportColumn,
  type ExportMeta,
} from '../lib/export';

interface ExportMenuProps<T> {
  fileName: string;
  title: string;
  subtitle?: string;
  meta?: ExportMeta[];
  columns: ExportColumn<T>[];
  rows: T[];
  note?: string;
  label?: string;
  disabled?: boolean;
}

type Job = 'pdf' | 'excel' | null;

/**
 * Two-format report export. The dropdown is portalled so it is never clipped by
 * the hero card; positions are recalculated from the trigger on every open.
 */
export default function ExportMenu<T>({
  fileName,
  title,
  subtitle,
  meta,
  columns,
  rows,
  note,
  label = 'تصدير التقرير',
  disabled = false,
}: ExportMenuProps<T>) {
  const triggerRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [job, setJob] = useState<Job>(null);
  const [status, setStatus] = useState('');
  const [failed, setFailed] = useState(false);
  const [position, setPosition] = useState({ top: 0, left: 0 });

  useEffect(() => {
    if (!open) return;
    const close = () => setOpen(false);
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    document.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  function toggle() {
    if (open) {
      setOpen(false);
      return;
    }
    const rect = triggerRef.current?.getBoundingClientRect();
    if (rect) {
      const width = 272;
      const left = Math.min(Math.max(12, rect.left), window.innerWidth - width - 12);
      const top = rect.bottom + 8 > window.innerHeight - 180 ? Math.max(12, rect.top - 178) : rect.bottom + 8;
      setPosition({ top, left });
    }
    setStatus('');
    setFailed(false);
    setOpen(true);
  }

  async function run(kind: Exclude<Job, null>) {
    setJob(kind);
    setFailed(false);
    setStatus(kind === 'pdf' ? 'جارٍ تجهيز ملف PDF…' : 'جارٍ تجهيز ملف Excel…');
    try {
      const options = { fileName, title, subtitle, meta, columns, rows, note };
      if (kind === 'pdf') await exportPdf(options);
      else await exportExcel(options);
      setStatus(`تم تنزيل ${kind === 'pdf' ? 'PDF' : 'Excel'} (${rows.length} سجل).`);
      setOpen(false);
    } catch (error) {
      setFailed(true);
      setStatus(error instanceof Error ? error.message : 'تعذر إنشاء الملف. أعد المحاولة.');
    } finally {
      setJob(null);
    }
  }

  const busy = job !== null;
  const dropdown =
    open && typeof document !== 'undefined'
      ? createPortal(
          <>
            <button className="export-scrim" aria-label="إغلاق قائمة التصدير" onClick={() => setOpen(false)} />
            <div className="export-dropdown" style={{ top: position.top, left: position.left }} role="menu">
              <button role="menuitem" onClick={() => void run('pdf')} disabled={busy || !rows.length}>
                {job === 'pdf' ? <Loader2 size={18} className="spin" /> : <FileText size={18} />}
                <span>
                  <strong>PDF — تقرير منسّق</strong>
                  <small>ترويسة رسمية، RTL، تذييل وأرقام صفحات</small>
                </span>
              </button>
              <button role="menuitem" onClick={() => void run('excel')} disabled={busy || !rows.length}>
                {job === 'excel' ? <Loader2 size={18} className="spin" /> : <FileSpreadsheet size={18} />}
                <span>
                  <strong>Excel — بيانات قابلة للتحليل</strong>
                  <small>ورقة RTL، فلاتر، تنسيق أرقام وألوان</small>
                </span>
              </button>
              {!rows.length && <div className="export-empty">لا توجد سجلات مطابقة للفلاتر الحالية.</div>}
            </div>
          </>,
          document.body,
        )
      : null;

  return (
    <div className="export-menu">
      <button
        ref={triggerRef}
        type="button"
        className="secondary-button export-trigger"
        onClick={toggle}
        disabled={disabled}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        {busy ? <Loader2 size={16} className="spin" /> : <Download size={16} />}
        {busy ? 'جارٍ التصدير…' : label}
        <ChevronDown size={14} className="chevron" />
      </button>
      {status && !open && (
        <span className={`export-status ${failed ? 'error' : ''}`} role="status">
          {status}
        </span>
      )}
      {dropdown}
    </div>
  );
}

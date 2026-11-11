/**
 * Client-side report export.
 *
 * PDF is produced by rendering a purpose-built RTL HTML sheet and rasterising it
 * with html2canvas-pro, which keeps Arabic shaping, ligatures and table layout
 * identical to what the browser shows (jsPDF's built-in fonts cannot shape Arabic).
 * Excel is produced with ExcelJS so sheets are genuinely right-to-left, styled and
 * numeric — not a renamed CSV.
 */

export interface ExportColumn<T> {
  header: string;
  value: (row: T) => string | number | null | undefined;
  /** Relative column width used by both PDF and Excel. */
  width?: number;
  align?: 'left' | 'right' | 'center';
}

export interface ExportMeta {
  label: string;
  value: string;
}

export interface ExportOptions<T> {
  fileName: string;
  title: string;
  subtitle?: string;
  meta?: ExportMeta[];
  columns: ExportColumn<T>[];
  rows: T[];
  note?: string;
  orientation?: 'portrait' | 'landscape';
}

const BRAND = {
  ink: '#10262f',
  inkSoft: '#3c5560',
  muted: '#6c8288',
  line: '#dfe8e5',
  emerald: '#0f766e',
  emeraldDeep: '#0a4d52',
  gold: '#a9813d',
  surface: '#ffffff',
  zebra: '#f6f9f8',
};

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function cellText(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  return String(value);
}

function stamp(): string {
  const now = new Date();
  return new Intl.DateTimeFormat('ar-YE', { dateStyle: 'long', timeStyle: 'short' }).format(now);
}

function fileStamp(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}`;
}

function safeName(name: string): string {
  return name.replace(/[\\/:*?"<>|]+/g, '-').replace(/\s+/g, ' ').trim();
}

function triggerDownload(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 4000);
}

function buildReportHtml<T>(options: ExportOptions<T>): string {
  const { title, subtitle, meta = [], columns, rows, note } = options;
  const totalWidth = columns.reduce((sum, column) => sum + (column.width ?? 1), 0);
  const colgroup = columns
    .map((column) => `<col style="width:${(((column.width ?? 1) / totalWidth) * 100).toFixed(3)}%">`)
    .join('');
  const head = columns
    .map((column) => `<th style="text-align:${column.align ?? 'right'}">${escapeHtml(column.header)}</th>`)
    .join('');
  const body = rows.length
    ? rows
        .map(
          (row, index) => `<tr style="background:${index % 2 ? BRAND.zebra : BRAND.surface}">
            ${columns
              .map(
                (column) =>
                  `<td style="text-align:${column.align ?? 'right'}">${escapeHtml(cellText(column.value(row)))}</td>`,
              )
              .join('')}
          </tr>`,
        )
        .join('')
    : `<tr><td colspan="${columns.length}" style="text-align:center;color:${BRAND.muted};padding:34px 0">لا توجد بيانات مطابقة للفلاتر الحالية.</td></tr>`;

  const metaHtml = meta.length
    ? `<div class="report-meta">${meta
        .map(
          (item) =>
            `<div><span>${escapeHtml(item.label)}</span><strong>${escapeHtml(item.value)}</strong></div>`,
        )
        .join('')}</div>`
    : '';

  // A fragment (not a full document) is returned on purpose: injecting a whole
  // `<!doctype html><html><head>` string makes `<meta>` the first element, and a
  // zero-sized first element rasterises to a blank PDF page.
  return `
    <div style="direction:rtl;width:1120px;padding:44px 46px 38px;background:${BRAND.surface};color:${BRAND.ink};font-family:'IBM Plex Sans Arabic','Segoe UI',Tahoma,sans-serif;">
      <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:24px;border-bottom:3px solid ${BRAND.emerald};padding-bottom:18px;">
        <div>
          <div style="display:flex;align-items:center;gap:10px;">
            <div style="width:34px;height:34px;border-radius:10px;background:linear-gradient(145deg,#159a8c,#0a4d52);color:#fff;display:flex;align-items:center;justify-content:center;font-size:21px;font-weight:700;">+</div>
            <div>
              <div style="font-size:10px;font-weight:700;letter-spacing:2.4px;color:${BRAND.emerald};">SMART HOSPITAL</div>
              <div style="font-size:9px;color:${BRAND.muted};">منظومة إدارة المستشفى الذكية</div>
            </div>
          </div>
          <div style="font-family:'Noto Kufi Arabic','IBM Plex Sans Arabic',sans-serif;font-size:25px;font-weight:600;line-height:1.45;margin-top:16px;">${escapeHtml(title)}</div>
          ${subtitle ? `<div style="font-size:12px;color:${BRAND.inkSoft};margin-top:5px;line-height:1.8;">${escapeHtml(subtitle)}</div>` : ''}
        </div>
        <div style="text-align:left;flex:0 0 auto;">
          <div style="font-size:9.5px;letter-spacing:.8px;color:${BRAND.muted};text-transform:uppercase;">تاريخ الإصدار</div>
          <div style="font-size:12px;font-weight:600;margin-top:3px;">${escapeHtml(stamp())}</div>
          <div style="margin-top:9px;display:inline-block;padding:4px 9px;border:1px solid ${BRAND.line};border-radius:99px;font-size:9px;color:${BRAND.gold};font-weight:600;">تقرير رسمي · RTL</div>
        </div>
      </div>
      ${metaHtml}
      <table style="width:100%;border-collapse:collapse;table-layout:fixed;margin-top:16px;">
        <colgroup>${colgroup}</colgroup>
        <thead><tr>${head.replaceAll('<th ', `<th style="background:${BRAND.emeraldDeep};color:#fff;font-size:10.5px;font-weight:600;padding:9px 11px;border:1px solid ${BRAND.emeraldDeep};white-space:nowrap;" `)}</tr></thead>
        <tbody>${body.replaceAll('<td ', `<td style="font-size:10.5px;color:${BRAND.inkSoft};padding:8px 11px;border:1px solid ${BRAND.line};line-height:1.7;word-wrap:break-word;" `)}</tbody>
      </table>
      <div style="display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-top:18px;padding-top:12px;border-top:1px solid ${BRAND.line};">
        <div style="font-size:9px;color:${BRAND.muted};line-height:1.9;max-width:720px;">
          ${escapeHtml(note ?? 'تقرير مُولَّد من منصة Smart Hospital. البيانات المعروضة تخضع لنطاق صلاحيات المستخدم والفرع المحدد.')}
        </div>
        <div style="font-size:9px;color:${BRAND.muted};white-space:nowrap;">عدد السجلات: ${rows.length}</div>
      </div>
    </div>
  `;
}

export async function exportPdf<T>(options: ExportOptions<T>): Promise<void> {
  const [{ jsPDF }, html2canvasModule] = await Promise.all([import('jspdf'), import('html2canvas-pro')]);
  const html2canvas = html2canvasModule.default;

  const host = document.createElement('div');
  host.setAttribute('aria-hidden', 'true');
  host.style.cssText = 'position:fixed;top:0;left:-12000px;z-index:-1;pointer-events:none;';
  host.innerHTML = buildReportHtml(options);
  document.body.appendChild(host);

  try {
    if (document.fonts?.ready) await document.fonts.ready;
    const sheet = host.firstElementChild as HTMLElement | null;
    if (!sheet || sheet.getBoundingClientRect().height < 20) {
      throw new Error('تعذر تجهيز قالب التقرير للطباعة.');
    }
    const canvas = await html2canvas(sheet, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
      width: 1120,
      windowWidth: 1120,
    });
    if (!canvas.width || !canvas.height) {
      throw new Error('تعذر تحويل التقرير إلى صورة صالحة.');
    }

    const orientation = options.orientation ?? (options.columns.length > 7 ? 'landscape' : 'portrait');
    const pdf = new jsPDF({ orientation, unit: 'mm', format: 'a4', compress: true });
    const pageWidth = pdf.internal.pageSize.getWidth();
    const pageHeight = pdf.internal.pageSize.getHeight();
    const margin = 9;
    const usableWidth = pageWidth - margin * 2;
    const usableHeight = pageHeight - margin * 2;
    const sourceWidth = canvas.width;
    const sliceHeight = Math.floor((sourceWidth * usableHeight) / usableWidth);

    let offset = 0;
    let page = 0;
    while (offset < canvas.height) {
      const height = Math.min(sliceHeight, canvas.height - offset);
      const slice = document.createElement('canvas');
      slice.width = sourceWidth;
      slice.height = height;
      const context = slice.getContext('2d');
      if (!context) throw new Error('تعذر تجهيز لوحة التقرير.');
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, sourceWidth, height);
      context.drawImage(canvas, 0, offset, sourceWidth, height, 0, 0, sourceWidth, height);

      if (page > 0) pdf.addPage();
      const renderedHeight = (height * usableWidth) / sourceWidth;
      pdf.addImage(slice.toDataURL('image/jpeg', 0.94), 'JPEG', margin, margin, usableWidth, renderedHeight);
      const footer = `Smart Hospital · ${page + 1}`;
      pdf.setFontSize(7);
      pdf.setTextColor(124, 145, 150);
      pdf.text(footer, pageWidth - margin, pageHeight - 4, { align: 'right' });
      offset += height;
      page += 1;
    }

    pdf.save(`${safeName(options.fileName)}_${fileStamp()}.pdf`);
  } finally {
    host.remove();
  }
}

export async function exportExcel<T>(options: ExportOptions<T>): Promise<void> {
  const module = await import('exceljs');
  const ExcelJS = (module as unknown as { default?: typeof import('exceljs') }).default ?? module;
  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'Smart Hospital';
  workbook.created = new Date();

  const sheet = workbook.addWorksheet(options.title.slice(0, 28) || 'تقرير', {
    views: [{ rightToLeft: true, showGridLines: false, state: 'frozen', ySplit: 4 }],
    pageSetup: { orientation: 'landscape', fitToPage: true, fitToWidth: 1, fitToHeight: 0, paperSize: 9 },
  });

  const columnCount = Math.max(options.columns.length, 2);
  sheet.columns = options.columns.map((column) => ({
    key: column.header,
    width: Math.max(14, Math.round((column.width ?? 1) * 22)),
  }));
  for (let index = options.columns.length; index < columnCount; index += 1) {
    sheet.getColumn(index + 1).width = 16;
  }

  const lastCol = sheet.getColumn(columnCount).letter;

  sheet.mergeCells(`A1:${lastCol}1`);
  const titleCell = sheet.getCell('A1');
  titleCell.value = options.title;
  titleCell.font = { name: 'IBM Plex Sans Arabic', size: 16, bold: true, color: { argb: 'FFFFFFFF' } };
  titleCell.alignment = { horizontal: 'right', vertical: 'middle', readingOrder: 'rtl' };
  titleCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF0A4D52' } };
  sheet.getRow(1).height = 36;

  sheet.mergeCells(`A2:${lastCol}2`);
  const subtitleCell = sheet.getCell('A2');
  subtitleCell.value = [options.subtitle, ...(options.meta ?? []).map((item) => `${item.label}: ${item.value}`)]
    .filter(Boolean)
    .join('  ·  ');
  subtitleCell.font = { name: 'IBM Plex Sans Arabic', size: 10, color: { argb: 'FF3C5560' } };
  subtitleCell.alignment = { horizontal: 'right', vertical: 'middle', readingOrder: 'rtl', wrapText: true };
  subtitleCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF3F7F6' } };
  sheet.getRow(2).height = 24;

  sheet.mergeCells(`A3:${lastCol}3`);
  const stampCell = sheet.getCell('A3');
  stampCell.value = `تاريخ الإصدار: ${stamp()}`;
  stampCell.font = { name: 'IBM Plex Sans Arabic', size: 9, color: { argb: 'FF6C8288' } };
  stampCell.alignment = { horizontal: 'right', vertical: 'middle', readingOrder: 'rtl' };
  sheet.getRow(3).height = 20;

  const headerRow = sheet.getRow(4);
  options.columns.forEach((column, index) => {
    const cell = headerRow.getCell(index + 1);
    cell.value = column.header;
    cell.font = { name: 'IBM Plex Sans Arabic', size: 10, bold: true, color: { argb: 'FFFFFFFF' } };
    cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF0F766E' } };
    cell.alignment = {
      horizontal: column.align === 'center' ? 'center' : column.align === 'left' ? 'left' : 'right',
      vertical: 'middle',
      readingOrder: 'rtl',
      wrapText: true,
    };
    cell.border = {
      top: { style: 'thin', color: { argb: 'FF0A4D52' } },
      bottom: { style: 'thin', color: { argb: 'FF0A4D52' } },
      left: { style: 'thin', color: { argb: 'FF0A4D52' } },
      right: { style: 'thin', color: { argb: 'FF0A4D52' } },
    };
  });
  headerRow.height = 28;

  const thin = { style: 'thin' as const, color: { argb: 'FFDFE8E5' } };
  options.rows.forEach((row, rowIndex) => {
    const excelRow = sheet.getRow(5 + rowIndex);
    options.columns.forEach((column, colIndex) => {
      const raw = column.value(row);
      const cell = excelRow.getCell(colIndex + 1);
      cell.value = raw === null || raw === undefined || raw === '' ? '—' : raw;
      cell.font = { name: 'IBM Plex Sans Arabic', size: 10, color: { argb: 'FF10262F' } };
      cell.alignment = {
        horizontal: column.align === 'center' ? 'center' : column.align === 'left' ? 'left' : 'right',
        vertical: 'middle',
        readingOrder: 'rtl',
        wrapText: true,
      };
      cell.border = { top: thin, bottom: thin, left: thin, right: thin };
      if (typeof raw === 'number') cell.numFmt = '#,##0.##';
      if (rowIndex % 2 === 1) {
        cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF6F9F8' } };
      }
    });
    excelRow.height = 22;
  });

  if (options.rows.length) {
    sheet.autoFilter = {
      from: { row: 4, column: 1 },
      to: { row: 4 + options.rows.length, column: Math.max(options.columns.length, 1) },
    };
  }

  const footerRow = sheet.getRow(6 + options.rows.length);
  sheet.mergeCells(`A${footerRow.number}:${lastCol}${footerRow.number}`);
  const footerCell = footerRow.getCell(1);
  footerCell.value =
    options.note ??
    'تقرير مُولَّد من منصة Smart Hospital. البيانات تخضع لنطاق صلاحيات المستخدم والفرع المحدد.';
  footerCell.font = { name: 'IBM Plex Sans Arabic', size: 9, italic: true, color: { argb: 'FF6C8288' } };
  footerCell.alignment = { horizontal: 'right', vertical: 'middle', readingOrder: 'rtl', wrapText: true };

  const buffer = await workbook.xlsx.writeBuffer();
  triggerDownload(
    new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
    `${safeName(options.fileName)}_${fileStamp()}.xlsx`,
  );
}

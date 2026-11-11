import { useLocation } from 'react-router-dom';
import { LayoutDashboard } from 'lucide-react';
import PageHeader from '../components/PageHeader';

const modules: Record<string, { title: string; subtitle: string; description: string; capabilities: string[]; status: string }> = {
  '/patients': { title: 'إدارة المرضى', subtitle: 'PATIENT MANAGEMENT', description: 'ملفات المرضى وبيانات التواصل والحساسية والمنح المرتبطة بفروع المستشفى.', capabilities: ['تسجيل وبحث المرضى', 'عرض الملف الطبي المصرح به', 'إدارة جهات الاتصال والحساسية', 'تتبع صلاحية الوصول بحسب الفرع'], status: 'واجهات API الأساسية موجودة — شاشة الإدارة قيد الاستكمال' },
  '/appointments': { title: 'المواعيد والاستقبال', subtitle: 'APPOINTMENT & RECEPTION', description: 'إدارة حجز المواعيد وتسجيل وصول المريض إلى العيادة.', capabilities: ['عرض الموعد وتفاصيله', 'التحقق من التعارض الزمني', 'تسجيل وصول المريض', 'الانتقال إلى الزيارة الطبية'], status: 'خدمات الحجز والتحقق موجودة — الشاشة قيد الاستكمال' },
  '/clinical': { title: 'الزيارات الطبية', subtitle: 'CLINICAL WORKSPACE', description: 'مساحة الطبيب والفريق التمريضي لتوثيق الزيارة والملاحظات والعلامات الحيوية.', capabilities: ['فتح الزيارة وربط فريق الرعاية', 'تسجيل العلامات الحيوية', 'كتابة الملاحظات وتوقيعها', 'تسجيل التشخيصات بواسطة الطبيب'], status: 'خدمات الزيارة والسجل السريري موجودة — الشاشة قيد الاستكمال' },
  '/laboratory': { title: 'إدارة المختبر', subtitle: 'LABORATORY', description: 'من دورة طلب الفحص وجمع العينة إلى النتيجة والمراجعة والاعتماد.', capabilities: ['قائمة طلبات الفحص', 'تسجيل جمع العينة ومعالجتها', 'إدخال النتائج', 'مراجعة واعتماد النتائج من المشرف'], status: 'سير عمل المختبر والرسائل الخلفية موجود — الشاشة قيد الاستكمال' },
  '/pharmacy': { title: 'الصيدلية والوصفات', subtitle: 'PHARMACY', description: 'إدارة الوصفات الطبية والصرف والمخزون حسب دفعات الدواء وتاريخ الصلاحية.', capabilities: ['استعراض الوصفات النشطة', 'التحقق من توفر الدواء', 'الصرف وفق FEFO', 'تتبع دفعات الدواء وحركات المخزون'], status: 'خدمات الوصفات والصرف موجودة — الشاشة قيد الاستكمال' },
  '/admissions': { title: 'التنويم وإدارة الأسرة', subtitle: 'ADMISSIONS & BEDS', description: 'إدارة التنويم وتخصيص الأسرة والنقل والخروج مع منع ازدواجية الإشغال.', capabilities: ['عرض حالات التنويم النشطة', 'استعراض الأسرة حسب الجناح', 'تخصيص السرير أو النقل', 'توثيق الخروج من التنويم'], status: 'خدمات التنويم والأسرة موجودة — الشاشة قيد الاستكمال' },
  '/billing': { title: 'الفواتير والمدفوعات', subtitle: 'BILLING & FINANCE', description: 'إدارة الفواتير وبنود الخدمات وإصدار الفواتير وتسجيل المدفوعات.', capabilities: ['عرض فاتورة وتفاصيلها', 'إصدار فاتورة بعد التحقق', 'تسجيل الدفعات', 'متابعة الرصيد المتبقي والإيصالات'], status: 'خدمات الفوترة والمدفوعات موجودة — الشاشة قيد الاستكمال' },
  '/inventory': { title: 'المخزون والمشتريات', subtitle: 'INVENTORY & PROCUREMENT', description: 'إدارة المخزون والدفعات والموردين وسياسات إعادة الطلب.', capabilities: ['متابعة رصيد الدفعات', 'مراقبة تواريخ الصلاحية', 'سياسات إعادة الطلب', 'الربط مع الصيدلية والاستلام'], status: 'الأساس الخلفي للمخزون موجود — الشاشات قيد الاستكمال' },
  '/analytics': { title: 'التحليلات الذكية', subtitle: 'SMART ANALYTICS', description: 'مؤشرات تشغيلية مجمعة وبطاقات قرار تفسيرية تدعم الإدارة دون اتخاذ قرارات علاجية آلية.', capabilities: ['مؤشرات الأداء اليومية', 'إشغال الأسرة وأداء المختبر', 'تنبيهات نقص المخزون', 'متابعة بطاقات القرار والإجراءات'], status: 'لوحة المؤشرات وبطاقات القرار متاحة في لوحة القيادة' },
  '/administration': { title: 'إدارة النظام', subtitle: 'SYSTEM ADMINISTRATION', description: 'إدارة إعدادات المستشفى والهوية التنظيمية والحسابات والأدوار.', capabilities: ['الفروع والأقسام', 'حسابات الموظفين', 'الأدوار والصلاحيات', 'إعدادات التشغيل والتدقيق'], status: 'التصميم الخلفي موجود — واجهات الإدارة قيد الاستكمال' },
};

export default function ModulePlaceholderPage() {
  const location = useLocation();
  const module = modules[location.pathname] ?? modules['/patients']!;
  return (
    <div className="module-page">
      <PageHeader eyebrow={module.subtitle} title={module.title} description={module.description} icon={LayoutDashboard} meta={['وحدة النظام']} />
      <section className="panel module-panel">
        <div className="module-illustration">✚</div>
        <span className="eyebrow">MODULE WORKSPACE</span>
        <h2>مساحة الوحدة جاهزة للربط</h2>
        <p>تم تصميم هذه الصفحة ضمن الهيكل العام للواجهة. ستُستكمل النماذج والجداول والتفاعلات فوق واجهات REST الموجودة، مع تطبيق صلاحيات الخادم.</p>
        <div className="capability-grid">{module.capabilities.map((item, index) => <div key={item}><span>0{index + 1}</span>{item}</div>)}</div>
        <div className="module-status"><span className="status-dot" />{module.status}</div>
      </section>
    </div>
  );
}

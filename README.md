# Smart Hospital — مشروع مؤسسي متكامل

> نظام إدارة مستشفى ذكي بواجهة عربية (RTL)، مبني على **Jakarta EE 10 + GlassFish 7.0.23 + MySQL 8.0.38 + React/TypeScript**، ويُسلَّم كـ **Enterprise Application (EAR)** جاهز للنشر.

---

## الحالة: ✅ مبني ومنشور ومُتحقَّق منه

- **المشروع القابل للتشغيل:** `smart-hospital/`
- **دليل التشغيل الكامل (ابدأ من هنا):** [`smart-hospital/README.md`](smart-hospital/README.md)
- **الواجهة:** <http://localhost:8080/>
- **حساب المدير التجريبي:** `hospitaladmin` / `Demo@2026`
- **قاعدة البيانات:** `hospital` على MySQL 8.0.38 (64 جدولاً + بيانات اصطناعية)
- **الخادم:** GlassFish 7.0.23 من `C:\server`، مصدر البيانات `jdbc/SmartHospitalDS`

تم التحقق فعلياً من: بناء Maven، نشر EAR، الروابط العميقة للواجهة، تسجيل الدخول بجميع الحسابات، وحدات REST الأساسية، ومصفوفة الصلاحيات لكل دور.

---

## البنية

```text
smart-hospital/
├─ pom.xml                  # المشروع الأب (Maven aggregator)
├─ smart-hospital-web/      # WAR: JAX-RS + EJB + JPA + واجهة React المبنية
├─ smart-hospital-ear/      # EAR: التجميع والنشر (context root = /)
├─ frontend/                # React + TypeScript + Vite
├─ database/mysql/          # سكربتات MySQL 8
├─ glassfish/               # إعداد مصدر البيانات وFile Realm
└─ docs/                    # التوثيق وOpenAPI وERD
```

---

## ابدأ سريعاً

```powershell
# 1) بناء المشروع
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
& 'C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd' `
  -B -ntp -f 'smart-hospital\pom.xml' package -DskipTests

# 2) نشر EAR
& 'C:\server\glassfish-7.0.23\glassfish7\bin\asadmin.bat' deploy --force=true `
  --name=smart-hospital 'smart-hospital\smart-hospital-ear\target\smart-hospital.ear'

# 3) فتح الواجهة
Start-Process 'http://localhost:8080/'
```

أو افتح مجلد `smart-hospital` مباشرة في **Apache NetBeans 21** كـ Maven Enterprise Application، ثم `Clean and Build` و`Run` على وحدة `smart-hospital-ear`.

---

## حزمة التخطيط الأصلية

مجلد `docs/` يحتوي حزمة التخطيط والتصميم (00–06) التي سبقت التنفيذ:

| الملف | المحتوى |
|-------|---------|
| [`docs/00-MASTER-PLAN.md`](docs/00-MASTER-PLAN.md) | التقييم، النطاق، القرارات |
| [`docs/01-ENVIRONMENT.md`](docs/01-ENVIRONMENT.md) | إعداد البيئة |
| [`docs/02-ARCHITECTURE-DATA.md`](docs/02-ARCHITECTURE-DATA.md) | المعمارية وERD |
| [`docs/03-SECURITY-RBAC.md`](docs/03-SECURITY-RBAC.md) | الأدوار والصلاحيات والتدقيق |
| [`docs/04-SMART-ANALYTICS.md`](docs/04-SMART-ANALYTICS.md) | المؤشرات وبطاقات القرار |
| [`docs/05-TESTING-ACCEPTANCE.md`](docs/05-TESTING-ACCEPTANCE.md) | القبول والتزامن والأمان |
| [`docs/06-ROADMAP.md`](docs/06-ROADMAP.md) | خطة العمل |

> الحزمة الأصلية كُتبت حول PostgreSQL كخيار تصميمي. **التنفيذ المُسلَّم يستخدم MySQL 8**، والسكربتات التاريخية لـPostgreSQL معزولة تحت `database/legacy-postgresql` و`docs/legacy-postgresql` للمرجعية فقط، ولا تُشغَّل.

---

## تنبيه

نظام أكاديمي ببيانات اصطناعية فقط. ليس نظاماً طبياً معتمداً، ولا يجوز استخدامه مع بيانات مرضى حقيقية أو لاتخاذ قرارات تشخيصية/علاجية.

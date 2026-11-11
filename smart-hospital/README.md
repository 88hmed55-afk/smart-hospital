# Smart Hospital — دليل التشغيل والجاهزية الإنتاجية

**الإصدار:** 1.0.0
**آخر تحقق تشغيلي:** 2026-11-10 على بيئة Windows الحالية (GlassFish 7.0.23 + MySQL 8.0.38)

نظام إدارة مستشفى مؤسسي بواجهة عربية (RTL) مبني على **Jakarta EE 10 + GlassFish 7.0.23 + MySQL 8.0.38 + React/TypeScript**، ويُحزَّم كـ **Enterprise Application (EAR)** واحد.

> **تنبيه:** هذه النسخة مهيّأة تقنياً للإنتاج بقدر ما يمكن معالجته بالكود، لكن **الامتثال الكامل لـ HIPAA لا يتحقق بالبرنامج وحده**. يلزم استكمال الضوابط المؤسسية والقانونية (تحليل مخاطر رسمي، تدريب، BAA، شهادة TLS موثوقة، تفعيل سياسة إلزام MFA، اختبار اختراق). راجع `docs/HIPAA_COMPLIANCE_MAPPING_AR.md` و`docs/PRODUCTION_READINESS_AR.md`.

---

## 1. حالة التسليم

| البند | الحالة |
|------|--------|
| بناء Maven متعدد الوحدات (EAR + WAR + واجهة React) | ✅ ناجح |
| اختبارات وحدة JUnit 5 | ✅ **39/39 ناجح** |
| النشر على GlassFish 7.0.23 باسم `smart-hospital` | ✅ ناجح |
| الواجهة على `http://localhost:8080/` | ✅ تعمل |
| الروابط العميقة `/login` `/dashboard` `/patients` … | ✅ تعمل (SPA fallback) |
| مصدر البيانات `jdbc/SmartHospitalDS` واختبار `ping-connection-pool` | ✅ ناجح |
| المصادقة عبر Bearer token وجلسات مخزّنة في `user_sessions` + MFA/TOTP | ✅ ناجح |
| فحص وحدات REST الأساسية (مرضى، مواعيد، تنويم، فواتير، محاسبة، مختبر، صيدلية، مشتريات، تحليلات، إشعارات، تدقيق، إعدادات) | ✅ 200 |
| فحوصات الدخان الآلية | ✅ **56/56 ناجح** |
| مصفوفة الصلاحيات لكل دور عبر `RequiresRoles` | ✅ مطابقة لتقييد الواجهة والخادم |
| سلسلة تدقيق غير قابلة للعبث + تحقق مستقل | ✅ `AUDIT_CHAIN_OK` |
| سكربتات النسخ الاحتياطي والاستعادة مع تحقق SHA-256 | ✅ مُختبرة فعلياً |
| ترحيلات قاعدة البيانات V001–V022 | ✅ مطبّقة ومسجلة في `schema_version` |

---

## 2. البنية

```text
smart-hospital/
├─ pom.xml                     # المشروع الأب (aggregator)
├─ smart-hospital-web/         # WAR: JAX-RS + EJB + JPA + واجهة React المبنية
│  └─ src/test/java/           # اختبارات JUnit 5
├─ smart-hospital-ear/         # EAR: تجميع ونشر، context root = /
├─ frontend/                   # React + TypeScript + Vite
├─ database/mysql/
│  ├─ migrations/              # ترحيلات V001–V022
│  └─ apply-migrations.ps1     # تطبيق الترحيلات مع تسجيل checksum
├─ glassfish/                  # سكربتات إعداد مصدر البيانات
├─ scripts/                    # بناء/نشر/فحص دخان/نسخ/استعادة/تحقق تدقيق
├─ docs/                       # التوثيق التشغيلي والأمني وواجهة OpenAPI وERD
└─ README.md                   # هذا الدليل
```

- **الواجهة مدمجة:** ناتج `frontend/dist` يُحزم داخل WAR عبر `maven-war-plugin`، لذا لا تحتاج خادم واجهة منفصل عند التشغيل العادي.
- **Context Root = `/`:** كل شيء تحت `http://localhost:8080/`، والـAPI تحت `http://localhost:8080/api/v1/`.
- **المخطط:** `hospital` (71 جدولاً: 64 أساسياً + جداول الجلسات/التدقيق/MFA/الإصدارات + بيانات تجريبية).
- **الوحدة المستهدفة:** Jakarta EE 10 Full Platform (EJB + JMS + MDB)، وليست Web Profile.

---

## 3. المتطلبات المثبتة على هذا الجهاز

| المكوّن | الإصدار / المسار |
|--------|-------------------|
| Apache NetBeans | 21 — `C:\Program Files\NetBeans-21` |
| JDK | 17.0.12 — `C:\Program Files\Java\jdk-17` |
| GlassFish | 7.0.23 — `C:\server\glassfish-7.0.23\glassfish7` |
| MySQL Server | 8.0.38 — الخدمة `MYSQL80` على المنفذ `3306` |
| MySQL Connector/J | 9.7.0 داخل `domain1\lib` |
| Node.js / npm | 26.4.0 / 11.17.0 |

---

## 4. بيانات هذه البيئة المحلية

> كل ما يلي مخصص للتطوير المحلي فقط. لا تُستخدم بيانات مرضى حقيقية.

### 4.1 قاعدة البيانات

- اسم القاعدة/المخطط: `hospital`
- مستخدم التطبيق: `smart_hospital_app`@`127.0.0.1` و`@localhost`
- كلمة مرور التطوير المحلية: `SmartHosp2026AppDb_7x`
- الصلاحيات: `SELECT, INSERT, UPDATE, DELETE` على `hospital.*` فقط (ليس root، ولا DDL).
- حساب الترحيل: `--login-path=smart-hospital-local` (يملك DDL، يُستخدم فقط بواسطة `apply-migrations.ps1`).

### 4.2 GlassFish

| العنصر | القيمة |
|-------|--------|
| المجال | `domain1` |
| Connection Pool | `SmartHospitalPool` (MysqlXADataSource) |
| JNDI | `jdbc/SmartHospitalDS` |
| Password Alias | `SmartHospitalDbPassword` |
| خصائص الاتصال | `connectionTimeZone=+00:00`, `forceConnectionTimeZoneToSession=false`, `sslMode=DISABLED`, `allowPublicKeyRetrieval=true` |
| بدء/إيقاف | `asadmin start-domain` / `asadmin stop-domain` |
| السجل | `C:\server\glassfish-7.0.23\glassfish7\glassfish\domains\domain1\logs\server.log` |

> `sslMode=DISABLED` مقبول للتطوير المحلي فقط. في الإنتاج استخدم `sslMode=VERIFY_IDENTITY` مع شهادة CA موثوقة.

### 4.3 حسابات المستخدمين التجريبية

الحسابات موجودة في جدول `user_accounts` (وليست File Realm)، وكلمة المرور الموحدة: **`Demo@2026`**.
كل حسابات العرض تحمل `must_change_password = TRUE`، ويُطلب تغيير كلمة المرور قبل الاستخدام الإنتاجي.

لإنشاء حساب إنتاجي جديد: **إدارة النظام → حسابات المستخدمين → حساب مستخدم**، وأدخل كلمة مرور أولية قوية. تُخزَّن كتجزئة PBKDF2 فقط، ويُطلب من الموظف تغييرها عند أول دخول.

| المستخدم | الدور |
|---------|------|
| `hospitaladmin` | `HOSPITAL_ADMIN` |
| `manager1` | `HOSPITAL_MANAGER` |
| `receptionist1` | `RECEPTIONIST` |
| `doctor1` | `DOCTOR` |
| `nurse1` | `NURSE` |
| `labtech1` | `LAB_TECHNICIAN` |
| `labsupervisor1` | `LAB_SUPERVISOR` |
| `pharmacist1` | `PHARMACIST` |
| `accountant1` | `ACCOUNTANT` |
| `inventoryofficer1` | `INVENTORY_OFFICER` |

رموز الأدوار في `user_accounts`/`user_roles` يجب أن تطابق رموز جدول `roles` داخل MySQL تماماً.

### 4.4 المصادقة والجلسات

- تسجيل الدخول: `POST /api/v1/auth/login` بالبريد/اسم المستخدم وكلمة المرور، ويعيد `token` معته.
- كل طلب لاحق: ترويسة `Authorization: Bearer <token>`.
- الرمز **معتم (opaque)** بطول 32 بايت عشوائي، ولا يُخزَّن إلا كتجزئة SHA-256 في `user_sessions`.
- كلمات المرور: **PBKDF2-HMAC-SHA256** بـ 210,000 تكرار وملح 16 بايت ومقارنة ثابتة الزمن.
- انتهاء الجلوس: **30 دقيقة خمول** و**8 ساعات حد أقصى** مطلق.
- قفل الحساب: **5 محاولات فاشلة → 15 دقيقة**، مع تسجيل `LOGIN_FAILED` في التدقيق.
- تغيير كلمة المرور يُبطل كل جلسات المستخدم الأخرى.
- **المصادقة الثنائية (MFA/TOTP):** عند تفعيلها للحساب يعيد الدخول `mfaRequired=true` وتحدياً صالحاً 5 دقائق بدل الجلسة، ثم يُستكمل عبر `POST /api/v1/auth/mfa/verify`. الأسرار مشفّرة بـAES-256-GCM، ورموز الاسترداد لمرة واحدة.
- تفاصيل التشغيل والمفتاح والإلزام: `docs/MFA_RUNBOOK_AR.md`.

---

## 5. البناء

### 5.1 من NetBeans 21

1. `File → Open Project`.
2. اختر مجلد **`smart-hospital`** الذي يحتوي ملف `pom.xml` الأب (وليس `smart-hospital-web` مباشرة).
3. ستظهر الوحدات الثلاث: `smart-hospital` و`smart-hospital-web` و`smart-hospital-ear`.
4. تأكد من `Tools → Java Platforms` أن JDK 17 مضاف.
5. من نافذة `Services → Servers` أضف GlassFish 7.0.23 واختر مجلد `C:\server\glassfish-7.0.23\glassfish7`.
6. شغّل `domain1`.
7. على المشروع الأب: `Clean and Build`.

> المشروع من نوع **Maven Enterprise Application**: ملف EAR الناتج هو
> `smart-hospital-ear\target\smart-hospital.ear`.
> `mvn package` يشغّل اختبارات JUnit تلقائياً (39 اختباراً). استخدم `-DskipTests` فقط للتكرار السريع محلياً.

### 5.2 من سطر الأوامر (PowerShell)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$mvn = 'C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd'

cd 'C:\Users\ZBook\Desktop\مجلد جديد\smart-hospital'
& $mvn -B -ntp clean package          # يبني + يشغّل 39 اختبار وحدة
```

أو استخدم السكربت الموحد:

```powershell
cd .\scripts
.\build-and-deploy.ps1                 # بناء + اختبارات + نشر
.\build-and-deploy.ps1 -SkipTests -BuildOnly   # بناء سريع فقط
```

لإعادة بناء الواجهة قبل التغليف:

```powershell
cd .\frontend
npm install
npm run build
cd ..
& $mvn -B -ntp package
```

---

## 6. النشر على GlassFish

### الخيار أ) من NetBeans

- `Run` على مشروع `smart-hospital-ear` بعد ربطه بخادم GlassFish المسجل.

### الخيار ب) من سطر الأوامر

```powershell
$asadmin = 'C:\server\glassfish-7.0.23\glassfish7\bin\asadmin.bat'

& $asadmin deploy --force=true --name=smart-hospital `
  'C:\Users\ZBook\Desktop\مجلد جديد\smart-hospital\smart-hospital-ear\target\smart-hospital.ear'
```

التحقق:

```powershell
& $asadmin list-applications
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8080/
```

### فحص مصدر البيانات

```powershell
& $asadmin ping-connection-pool SmartHospitalPool
```

إذا فشل الاتصال، تحقق من: تشغيل `MYSQL80`، صحة كلمة المرور في alias، وجود Connector/J داخل `domain1\lib`، وأن قاعدة `hospital` موجودة.

### HTTPS (اختياري)

الخادم يستمع على `8181` بشهادة self-signed للتطوير. لفرض إعادة توجيه HTTPS:

```powershell
# على مستوى JVM
& $asadmin create-jvm-options "-Dsmarthospital.forceHttps=true"
# أو متغير بيئة
$env:SMART_HOSPITAL_FORCE_HTTPS='true'
```

في الإنتاج: شهادة من CA موثوق، TLS 1.2+، و`sslMode=VERIFY_IDENTITY` لمصدر البيانات.

---

## 7. ترحيلات قاعدة البيانات

كل تغيير مخطط يُنفَّذ عبر ملف مرقّم في `database/mysql/migrations` ويُسجَّل في `hospital.schema_version` مع بصمة SHA-256.

```powershell
cd .\database
powershell -ExecutionPolicy Bypass -File .\apply-migrations.ps1 -LoginPath smart-hospital-local
```

- يُطبِّق الملفات غير المطبّقة فقط، بالترتيب.
- يسجّل بصمة SHA-256 لكل ترحيل عند تطبيقه لأغراض التدقيق، ويتخطّى ما هو مسجّل مسبقاً.
- السلسلة الحالية: **V001–V022**.

| الترحيل | الغرض |
|---|---|
| V012 | مصادقة التطبيق: `user_sessions`, `user_accounts`, كلمات مرور مُجزّأة |
| V013 | تقوية التدقيق: سلسلة التجزئة `audit_chain_state` |
| V014 | سلامة الأدوية: الحساسية والتفاعلات الدوائية |
| V015 | قفل تفاؤلي `version` للكيانات الأساسية |
| V016 | الموافقات وأكواد المعايير |
| V017 | فصل مفاتيح التدقيق الأجنبية (append-only) |
| V018 | توسيع قيود CHECK للحساسية/الموافقات |
| V019 | `audit_logs.hash_payload` للتحقق المستقل من السلسلة |
| V020 | MFA/TOTP: أسرار مشفّرة، تحديات دخول، رموز استرداد |
| V021 | إصلاح قيدَي JSON: مقارنة `JSON_TYPE()` بالأحرف الكبيرة (`OBJECT`/`ARRAY`) |
| V022 | ضمان وجود `purchase_order_items.received_quantity` في كل التركيبات |

---

## 8. التشغيل والاستخدام

1. افتح: **<http://localhost:8080/>**
2. سجّل الدخول بحساب من الجدول أعلاه (مثال: `hospitaladmin` / `Demo@2026`).
3. تظهر القائمة الجانبية حسب دور المستخدم، مع مبدّل الفرع في الشريط العلوي.

> بعد إعادة تشغيل الجهاز: شغّل `.\scripts\start-local-stack.ps1` (يشغّل MySQL وGlassFish وينتظر جاهزية التطبيق). خدمة MySQL مضبوطة على التشغيل التلقائي.

### سلوك الجلسة (مهم)

- رمز الدخول يُحفظ **في الذاكرة فقط** ولا يُخزَّن في `localStorage` أو `sessionStorage`.
- لذلك **إعادة تحميل الصفحة (F5) تعيدك إلى `/login`** بشكل مقصود.
- زر «تسجيل الخروج» يستدعي `POST /api/v1/auth/logout` ويبطل الجلسة على الخادم ثم يعود إلى `/login`.

---

## 8.1 الواجهة وتصدير التقارير (v0.18)

**نظام التصميم:** `frontend/src/styles/styles.css` يحتفظ بالبنية التخطيطية، وتُحمَّل بعده ثلاث طبقات: `base.css` (الرموز البصرية والخطوط والألوان الفاتحة/الداكنة) ← `components.css` (الهيدر، الأزرار، القوائم، الجداول، شارات الحالة) ← `pages.css` (تنسيقات الصفحات والطباعة). الخطوط: **IBM Plex Sans Arabic** للنصوص و**Noto Kufi Arabic** للعناوين، محزومة محلياً عبر `@fontsource` (بلا اتصال خارجي).

**الهيدر الموحّد:** مكوّن `PageHeader` واحد في كل صفحة: ترويسة داكنة متدرجة، تحية حسب وقت اليوم، اسم المستخدم ودوره، الفرع الحالي، التاريخ، ورقائق سياق خاصة بالصفحة، مع أفقيّة الإجراءات (تصدير/إضافة/تحديث).

**الشل:** `HospitalLayout` مع قائمة جانبية مجمّعة، بحث فوري عبر `Ctrl+K`، مبدّل فرع، تبديل فاتح/داكن محفوظ في `localStorage`، وجرس إشعارات.

**التصدير (من الواجهة):** زر «تصدير التقرير» في كل صفحة تقارير يفتح قائمة PDF/Excel:
- **PDF:** صفحة تقرير RTL مبنية بـHTML ثم تُحوَّل صورة عالية الدقة عبر `html2canvas-pro` وتُقسَّم صفحات A4 في `jsPDF` (ترويسة رسمية، بيانات وصفية، تذييل، أرقام صفحات). الطريقة تحافظ على تشكيل العربية وربط الحروف.
- **Excel:** مصنّف حقيقي عبر `ExcelJS` بورقة `rightToLeft`، رؤوس ملوّنة، تثبيت صفوف، فلتر تلقائي، تنسيق أرقام وتنسيق شرطي للأرقام.

**تم التحقق فعلياً في هذه البيئة:** تصدير «سجل المرضى» (PDF ~133KB بصفحة واحدة، Excel ~7.8KB بورقة RTL تحتوي 7 صفوف)، وتصدير «سجل التدقيق» (30 سجلاً → PDF من صفحتين، ~834KB). تُبنى الصفحات من البيانات المعروضة فعلياً بعد تطبيق الفلاتر، ويُعطَّل التصدير عند عدم وجود سجلات.

---

## 8.2 البيانات التجريبية (ستة أشهر)

يحتوي المستودع على مولّد بيانات اصطناعية متكامل في `scripts/demo-data/` يبني تاريخاً تشغيلياً ومالياً متسقاً يمتد **183 يوماً في الماضي** و**30 يوماً من الحجوزات المستقبلية**، لاختبار التقارير والاتجاهات والفواتير والمخزون وسلسلة التدقيق على بيانات كثيفة.

```powershell
node scripts/demo-data/seed.js      # توليد (220 مريضاً، 140 تنويم، 16 نشطاً افتراضياً)
node scripts/demo-data/verify.js    # 83 فحص اتساق
```

| المجال | الحجم الناتج |
|---|---|
| مرضى / مواعيد / زيارات | 220 / 2,139 / 1,783 |
| تنويم / وصفات | 140 (16 نشطة) / 1,092 |
| مختبر | 816 طلبات و1,507 نتيجة |
| صرف دوائي | 979 عملية و2,024 عنصراً |
| مخزون ومشتريات | 32 أمر شراء، 2,168 حركة، 90 رصيداً |
| فواتير ومدفوعات | 1,558 فاتورة و1,498 دفعة |
| محاسبة | 2,997 قيداً متوازناً (≈ 42.4 مليون ر.ي) |
| تحليلات | 1,656 لقطة يومية، 10 بطاقات قرار، 18 تنبيهاً، 42 إشعاراً |

> الأرقام من تشغيل موثّق في 11 نوفمبر 2026، وقد تتفاوت قليلاً بين تشغيل وآخر بسبب العشوائية وتوقيت التشغيل.

**السلامة:** المولّد لا يعدّل أو يحذف أي صف موجود، ولا يمسّ `user_accounts` أو `user_sessions` أو `audit_logs`. كل صف يحمل بادئة معرّف محجوزة (`deadbeef…` للمعاملات و`feedface…` للمرجعي)، ويرفض إعادة البذر إذا وُجدت. إعادة البذر تتطلب استعادة النسخة الاحتياطية النظيفة أولاً.

**«اليوم» في لوحة القيادة:** الطوابع الزمنية بـUTC وتُعرض بتوقيت الفرع (UTC+3). إذا جرى البذر بعد منتصف الليل بقليل تكون مؤشرات اليوم (زيارات/إيراد/متوسط مختبر) قريبة من الصفر بينما تظهر مواعيد اليوم والتنويم النشط والمختبر المعلّق بأرقام حقيقية — سلوك متوقع لنظام يومي وليس نقصاً في البيانات.

**المتطلبات المسبقة:** V021 وV022 مطبّقان (قيدا JSON بالأحرف الكبيرة، و`purchase_order_items.received_quantity`). التفاصيل الكاملة وإجراء إعادة البذر واستكشاف الأخطاء في **`scripts/demo-data/README_AR.md`**.

---

## 9. تشغيل الواجهة أثناء التطوير (اختياري)

التشغيل العادي لا يحتاجه لأن الواجهة مبنية داخل EAR. أما أثناء تعديل React:

```powershell
cd .\frontend
Copy-Item .env.example .env   # إن وُجد
npm install
npm run dev
```

- الرابط: <http://localhost:5173>
- Vite يوجّه `/api` إلى `http://localhost:8080` (التطبيق المنشور على الجذر).
- بعد الانتهاء نفّذ `npm run build` وأعد بناء WAR/EAR لنقل التغييرات إلى النشر.

---

## 10. اختبارات الدخان (Smoke Tests)

سكربت جاهز (غير مُدمّر) يفحص الواجهة والروابط العميقة والمصادقة والوحدات الأساسية وتقييد الأدوار:

```powershell
cd 'C:\Users\ZBook\Desktop\مجلد جديد\smart-hospital'
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

النتيجة المتوقعة:

```text
RESULT: 56 passed, 0 failed
```

مثال يدوي لأهم النقاط (Bearer token):

```powershell
$base = 'http://localhost:8080/api/v1'
$token = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType 'application/json' `
  -Body '{"username":"hospitaladmin","password":"Demo@2026"}').token
$h = @{ Authorization = "Bearer $token" }

# الهوية والدور
Invoke-RestMethod -Uri "$base/security/me" -Headers $h

# مؤشرات الأمان (HOSPITAL_ADMIN فقط، بلا PHI)
Invoke-RestMethod -Uri "$base/security/metrics" -Headers $h

# الصحة
Invoke-RestMethod -Uri "$base/health" -Headers $h
Invoke-RestMethod -Uri "$base/health/ready" -Headers $h

# لوحة التحليلات (استبدل معرف الفرع إن لزم)
Invoke-RestMethod -Uri "$base/analytics/dashboard?branchId=10000000-0000-4000-8000-000000000001" -Headers $h
```

توقّع `401` عند غياب/انتهاء الرمز، و`403` عند الوصول لدور غير مصرح له (وهذا سلوك صحيح لا خطأ).

---

## 11. مصفوفة الأدوار والصفحات

| الدور | الصفحات المتاحة |
|------|-------------------|
| `HOSPITAL_ADMIN` | لوحة القيادة، المرضى، المواعيد، التنويم، الفواتير، المحاسبة، المخزون، التحليلات، إدارة النظام، الإعدادات، التدقيق، الأمان |
| `HOSPITAL_MANAGER` | لوحة القيادة، المواعيد، التنويم، الفواتير، المحاسبة، المخزون، التحليلات، الإعدادات، التدقيق، الأمان |
| `ACCOUNTANT` | لوحة القيادة، الفواتير، المحاسبة، التحليلات (مؤشرات دون بطاقات قرار إدارية)، الأمان |
| `RECEPTIONIST` | المرضى، المواعيد، الأمان |
| `DOCTOR` | المرضى، المواعيد، الزيارات الطبية، التنويم، الأمان |
| `NURSE` | المرضى، الزيارات الطبية، التنويم، الأمان |
| `LAB_TECHNICIAN` / `LAB_SUPERVISOR` | المختبر، الأمان |
| `PHARMACIST` | الصيدلية، الأمان |
| `INVENTORY_OFFICER` | المخزون والمشتريات، الأمان |

التقييد مطبّق مرتين: في الواجهة (إخفاء الروابط) وفي الخادم عبر **`@RequiresRoles`** المخصص الذي يفرضه `AuthContextFilter` بعد التحقق من Bearer token. القائمة المعروضة في الواجهة مبنية من المصدر الواحد `frontend/src/auth/pageAccess.ts`، وهي مطابقة حرفياً لتعليقات `@RequiresRoles` على نقاط النهاية للقراءة؛ لذلك لا تظهر صفحة يمكن أن تنتهي بـ`403`. أي زيارة مباشرة بالرابط خارج النطاق تُعيد المستخدم إلى صفحته الرئيسية.

> **لماذا لا نستخدم `@RolesAllowed`؟** حزمة `microprofile-jwt-auth` المرفقة مع GlassFish تسجّل `RolesAllowedDynamicFeature` تلقائياً وتحوّل كل مورد محمي إلى مصادقة حاوية BASIC (استجابة HTML 401). لذلك استُبدلت بـ`RequiresRoles` المخصص مع تقييد فحص Jersey على حزمة `com.smarthospital` فقط.

---

## 12. الضوابط الأمنية المُنفّذة

| المجال | التنفيذ |
|---|---|
| المصادقة | Bearer tokens معتمة + PBKDF2-HMAC-SHA256 (210k) + قفل بعد 5 محاولات |
| الجلسات | خمول 30 دقيقة، حد أقصى 8 ساعات، إبطال جماعي عند تغيير كلمة المرور |
| دورة كلمة المرور | خيار إنتاجي يحجب كل المسارات (عدا `security/me` و`auth/password` و`auth/logout`) حتى تُغيَّر كلمة المرور الإجبارية |
| المصادقة الثنائية | TOTP RFC 6238 + AES-256-GCM للأسرار + رموز استرداد لمرة واحدة + إلزام حسب الدور |
| الصلاحيات | `RequiresRoles` + نطاق فروع `user_branch_access` + وصول مرضى `patient_branch_access` |
| التدقيق | سلسلة SHA-256 + قفل `audit_chain_state` + مشغلات منع UPDATE/DELETE + `hash_payload` |
| سلامة الأدوية | فحص حساسية وتفاعلات دوائية قبل الوصفة، ومنع الخطير إلا بمبرر ≥10 أحرف مُدقّق |
| تكامل البيانات | `@Version` على الكيانات الأساسية + قيود CHECK + مفاتيح أجنبية |
| رؤوس الأمان | CSP، `X-Frame-Options: DENY`، `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `Cache-Control: no-store` |
| الحمولات | رفض JSON المشوّه بـ`400 MALFORMED_JSON` |
| صيانة المصادقة | مهمة ليلية (02:15) تحذف الجلسات المنتهية/المبطلة الأقدم من 30 يوماً وتفك الأقفال المنتهية، مع تدقيق `SYSTEM_MAINTENANCE` |
| الأسرار | كلمة مرور DB في alias داخل GlassFish؛ لا أسرار في الكود |

### التحقق من سلسلة التدقيق

```powershell
cd .\scripts
.\verify-audit-chain.ps1
```

يفحص أن كل صف يحمل تجزئة تطابق حمولته المحفوظة، وأن الروابط متصلة، وأن رأس السلسلة يطابق `audit_chain_state`. النتيجة المتوقعة: `AUDIT_CHAIN_OK`.
يمكن أيضاً استدعاء `GET /api/v1/audit-logs/integrity` بحساب `HOSPITAL_ADMIN`.

### مؤشرات الأمان

`GET /api/v1/security/metrics` (لـ`HOSPITAL_ADMIN`) يعيد: الجلسات النشطة، الحسابات المقفلة، فشل/نجاح الدخول 24 ساعة، حسابات تنتظر تغيير كلمة المرور، سلامة سلسلة التدقيق، وحالة HTTPS — **دون أي PHI**.

### صيانة حالة المصادقة

- مهمة مجدولة يومياً (02:15) في `SessionMaintenanceService`:
  - تحذف الجلسات المنتهية أو المبطلة الأقدم من 30 يوماً (تبقي الأحدث للتحليل الجنائي).
  - تفك الأقفال المنتهية وتصفّر عدّاد فشل الدخول.
  - تكتب حدث `SYSTEM_MAINTENANCE` في سلسلة التدقيق عند وجود تغييرات فقط.
- التشغيل اليدوي الفوري (HOSPITAL_ADMIN فقط):
  ```powershell
  Invoke-RestMethod -Method Post -Uri "$base/system/maintenance/cleanup" -Headers $h
  ```
  يعيد `{ enabled, retentionDays, prunedSessions, releasedLockouts, executedAt }`.
- التعطيل: `-Dsmarthospital.maintenance.enabled=false` أو `SMART_HOSPITAL_MAINTENANCE_ENABLED=false`.
- ضبط مدة الاحتفاظ: `-Dsmarthospital.maintenance.sessionRetentionDays=30`.

### إنفاذ تغيير كلمة المرور الإجباري (للإنتاج)

عند ضبط `-Dsmarthospital.security.enforcePasswordRotation=true` أو `SMART_HOSPITAL_ENFORCE_PASSWORD_ROTATION=true`، أي حساب يحمل `must_change_password=TRUE` يُحجب عن كل المسارات باستثناء `security/me` و`auth/password` و`auth/logout`، ويُعاد `403 PASSWORD_CHANGE_REQUIRED`. القيمة الافتراضية `false` لتسهيل بيئة العرض؛ **يجب تفعيلها في الإنتاج** بعد إنشاء الحسابات. الحالة ظاهرة في `security/metrics` عبر `passwordRotationEnforced`. وعند التفعيل تعرض الواجهة شاشة `/change-password` تلقائياً بعد الدخول، وبعد نجاح التغيير تُبطَل كل الجلسات ويُعاد المستخدم إلى تسجيل الدخول.

### المصادقة الثنائية (MFA/TOTP)

- **مفتاح التشفير:** `-Dsmarthospital.mfa.key=<BASE64_32B>` أو `SMART_HOSPITAL_MFA_KEY`. إن غاب يُنشأ ملف محلي `~/.smart-hospital/mfa.key` (للتطوير فقط). يجب توفير المفتاح ونسخه احتياطياً في الإنتاج — راجع `docs/MFA_RUNBOOK_AR.md`.
- **إلزام حسب الدور:** `-Dsmarthospital.mfa.requiredRoles=HOSPITAL_ADMIN,HOSPITAL_MANAGER,DOCTOR,PHARMACIST` أو `SMART_HOSPITAL_MFA_REQUIRED_ROLES`. القيمة الفارغة = اختياري.
- **نقاط النهاية:**
  - `GET /api/v1/auth/mfa/status`
  - `POST /api/v1/auth/mfa/enroll/start` ثم `POST /api/v1/auth/mfa/enroll/confirm`
  - `POST /api/v1/auth/mfa/verify` (خطوة الدخول الثانية)
  - `POST /api/v1/auth/mfa/disable` و`POST /api/v1/auth/mfa/recovery-codes/regenerate` (تتطلبان كلمة المرور + رمزاً صالحاً)
  - `PATCH /api/v1/admin/accounts/{accountId}/mfa-reset` (HOSPITAL_ADMIN فقط، بسبب موثق، ولا يصلح للمسؤول نفسه)
- **الواجهة:** خطوة ثانية في `/login`، شاشة `/mfa-setup`، وصفحة `/security`. عمود MFA وإجراء إعادة التعيين في **إدارة النظام**.
- **المؤشرات:** `mfaEnrollmentEnforced`, `mfaRequiredRoles`, `mfaEnabledAccounts`, `accountsPendingMfaEnrollment`, `mfaChallengesIssued24h`, `mfaVerifications24h`, `mfaFailures24h`.
- **دليل كامل:** `docs/MFA_RUNBOOK_AR.md`.

---

## 13. النسخ الاحتياطي والاستعادة

### نسخة احتياطية

```powershell
cd .\scripts
.\backup-database.ps1 -BackupDirectory 'D:\backups\smart-hospital' -RetentionDays 14
```

ينتج `hospital-YYYYMMDD-HHmmss.sql` + ملف `.sha256`، ويطبع `BACKUP_OK` عند النجاح.

### استعادة إلى قاعدة منفصلة (الافتراضي الآمن)

```powershell
.\restore-database.ps1 -BackupFile 'D:\backups\smart-hospital\hospital-20261110-020000.sql'
```

يتحقق من SHA-256 ومحتوى النسخة، ثم يُنشئ `hospital_restore_<timestamp>`.
لا تُستعاد فوق `hospital` إلا بـ`-AllowOverwrite -DropAndRecreate -ConfirmToken 'RESTORE hospital'` وبعد موافقة مالك النظام.

### بعد كل استعادة

1. `.\verify-audit-chain.ps1` → يجب `AUDIT_CHAIN_OK`.
2. `.\smoke-test.ps1` → يجب 56/56.
3. راجع `security/metrics` للتأكد من الجلسات والحسابات.

التفاصيل الكاملة في `docs/BACKUP_DR_RUNBOOK_AR.md`.

---

## 14. ملاحظات تقنية مهمة

- **استعلامات EclipseLink الأصلية (Native SQL):** تستخدم معاملات موضعية `?1, ?2, …` وليس `:name`؛ لأن EclipseLink لا يدعم المعاملات المسمّاة في `createNativeQuery`.
- **`Instant`:** يمر عبر `InstantAttributeConverter` لتفادي تحويله إلى `byte[]` على MySQL.
- **`UUID`:** يمر عبر `UuidAttributeConverter` (آمن للقيم الفارغة) لتفادي NPE في الأعمدة القابلة لـ`NULL` مثل `parent_account_id`.
- **SPA Fallback:** `SpaFallbackFilter` يعيد توجيه مسارات React غير الموجودة إلى `index.html`، مع تمرير `/api/` و`/assets/` كما هي.
- **سياق التخطيط:** المسارات المحمية داخل `RequireRoles` تعيد تمرير `{ branchId, branch }` عبر `<Outlet context=…>` حتى لا تفقد الصفحات سياق الفرع.
- **الاستعلامات لا تُنفَّذ قبل توفر `branchId`:** لتفادي طلبات `400` عابرة عند أول تحميل.
- **`audit_logs.occurred_at`:** هو عمود الوقت الفعلي (وليس `created_at`).
- **`prescriptions.branch_id`:** مطلوب، ويُشتق من زيارة المريض (`encounter.branchId`).

---

## 15. استكشاف الأخطاء

| العرض | السبب المحتمل | المعالجة |
|------|----------------|----------|
| `401 AUTHENTICATION_REQUIRED` | لا يوجد Bearer token أو انتهت الجلسة | سجّل الدخول مجدداً عبر `/auth/login` |
| `401` مع `WWW-Authenticate: Basic` | حزمة JWT/Jersey تقوم بفحص حاوية | تأكد من استخدام `RequiresRoles` وليس `@RolesAllowed`، ومن تقييد فحص Jersey على `com.smarthospital` |
| `403 Forbidden` | الدور لا يملك الصلاحية | سلوك صحيح؛ استخدم حساباً بالدور المناسب |
| `403 STAFF_PROFILE_REQUIRED` | الحساب غير مرتبط بسجل موظف (`staff_id`) | اربط الحساب بموظف أو استخدم حساباً سريرياً مناسباً |
| `403 PATIENT_ACCESS_DENIED` | لا يوجد وصول لفرع المريض | راجع `patient_branch_access` و`user_branch_access` |
| `400 MALFORMED_JSON` | جسم الطلب ليس JSON صالحاً | صحّح الحمولة |
| `404` لرابط عميق مثل `/patients` | حزمة قديمة بلا `SpaFallbackFilter` | أعد بناء EAR ونشره |
| `409 MEDICATION_SAFETY_BLOCKED` | حساسية أو تفاعل دوائي خطير | راجع المبرر السريري أو استخدم `overrideReason` موثقاً |
| `500 Check constraint 'ck_allergy_verification'` | قيمة حالة غير مسموحة | استخدم `UNVERIFIED/VERIFIED/CONFIRMED/REFUTED/ENTERED_IN_ERROR/RULED_OUT` |
| `500 Field 'branch_id' doesn't have a default value` | نسخة قديمة من كيان الوصفة | أعد البناء والنشر من المصدر الحالي |
| فشل النشر: JMS/EJB غير موجود | توزيعة Web Profile | استخدم GlassFish Full Platform 10 |
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | Connector/J غير موجود | ضع JAR في `domain1\lib` وأعد تشغيل المجال |
| إعادة التحميل تعيد إلى الدخول | سلوك مقصود (token في الذاكرة) | سجّل الدخول مجدداً |
| `AUDIT_CHAIN_BROKEN` | تلاعب أو استعادة جزئية أو فجوة روابط | أوقف الكتابة، خذ نسخة، وحقّق في `audit_logs` و`audit_chain_state` |

سجل الأخطاء التفصيلي:

```powershell
Get-Content 'C:\server\glassfish-7.0.23\glassfish7\glassfish\domains\domain1\logs\server.log' -Tail 200
```

---

## 16. الوثائق المرجعية

| الملف | المحتوى |
|-------|---------|
| `docs/HIPAA_COMPLIANCE_MAPPING_AR.md` | تعيين ضوابط HIPAA إلى المنصة والفجوات المؤسسية |
| `docs/PRODUCTION_READINESS_AR.md` | تقييم الجاهزية الإنتاجية والمتطلبات المتبقية |
| `docs/GO_LIVE_CHECKLIST_AR.md` | قائمة تحقق الإطلاق خطوة بخطوة |
| `docs/SECURITY_POLICY_AR.md` | سياسة أمن المعلومات القابلة للاعتماد |
| `docs/INCIDENT_RESPONSE_AR.md` | خطة الاستجابة للحوادث والإبلاغ |
| `docs/RISK_ASSESSMENT_AR.md` | سجل تقييم المخاطر وخطة المعالجة |
| `docs/BACKUP_DR_RUNBOOK_AR.md` | النسخ الاحتياطي والتعافي من الكوارث |
| `docs/MFA_RUNBOOK_AR.md` | تشغيل المصادقة الثنائية: المفتاح، الإلزام، فقدان الجهاز، وإعادة التعيين |
| `docs/NETBEANS_WINDOWS_SETUP_AR.md` | إعداد NetBeans وMySQL وGlassFish خطوة بخطوة |
| `docs/EE10_MYSQL_PORTING.md` | قرارات التوافق والقيود |
| `docs/EE10_MYSQL_STATIC_REVIEW_AR.md` | مراجعة المصدر ونتائج التحقق |
| `docs/api/openapi-v1.yaml` | عقد REST API |
| `docs/api/ERD_mysql_v1_0.mmd` | مخطط ERD لقاعدة MySQL |
| `glassfish/README.md` | إعداد Connector/J ومصدر البيانات |
| `scripts/demo-data/README_AR.md` | دليل المولّد: الخيارات، المعرّفات، إعادة البذر، والتحقق |
| `database/mysql/migrations/` | ترحيلات V001–V022 |
| `.github/workflows/ci.yml` | خط CI (بناء الواجهة + اختبارات Maven + حزمة EAR) |

### السكربتات

| السكربت | الغرض |
|---|---|
| `scripts/build-and-deploy.ps1` | بناء + اختبارات + نشر |
| `scripts/start-local-stack.ps1` | تشغيل MySQL وGlassFish والتحقق من جاهزية التطبيق بعد إعادة التشغيل |
| `scripts/smoke-test.ps1` | 56 فحص دخان وظيفي وأمني |
| `scripts/verify-audit-chain.ps1` | تحقق مستقل من سلسلة التدقيق |
| `scripts/backup-database.ps1` | نسخة احتياطية متسقة + SHA-256 |
| `scripts/restore-database.ps1` | استعادة آمنة مع تحقق |
| `scripts/demo-data/seed.js` | توليد بيانات تجريبية متكاملة لستة أشهر (غير هدّام) |
| `scripts/demo-data/verify.js` | 83 فحص اتساق للبيانات التجريبية |
| `database/apply-migrations.ps1` | تطبيق ترحيلات المخطط |

---

## 17. حدود الاستخدام والامتثال

هذه المنصة تطبيق مؤسسي ببيانات اصطناعية للعرض والتطوير، ومهيّأة تقنياً للإنتاج بعد استكمال البنود المؤسسية التالية:

1. **تفعيل سياسة إلزام MFA** للمسؤولين والأطباء وتسجيل جميع حساباتهم (البنية منفّذة بالكامل).
2. **شهادة TLS موثوقة** وإلزام HTTPS بدل self-signed.
3. **تشفير At Rest** للأقراص والنسخ الاحتياطية.
4. **إدارة أسرار** عبر خزنة (Vault/KMS) بدل الملفات المحلية، بما فيها مفتاح MFA.
5. **اختبار اختراق ومراجعة كود مستقلة**.
6. **BAA** مع مزود الاستضافة وأي معالج فرعي.
7. **أرشفة تدقيق WORM لمدة 6 سنوات**.
8. **إزالة بيانات العرض** وتغيير كلمات مرور الحسابات قبل الإنتاج.
9. **مراجعة قانونية محلية** (مثل PDPL) إضافة إلى HIPAA عند التعامل مع بيانات أمريكية.

**لا يجوز** استخدام هذه النسخة لحفظ معلومات مرضى حقيقية أو لاتخاذ قرارات تشخيصية/علاجية قبل استكمال البنود أعلاه واعتماد `docs/GO_LIVE_CHECKLIST_AR.md`.

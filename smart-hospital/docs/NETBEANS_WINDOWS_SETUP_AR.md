# دليل إعداد وتشغيل Smart Hospital على Windows

**هذه النسخة معدّة لبيئة جهازك الحالية، ولا تتطلب ترقية JDK أو GlassFish:**

- Apache NetBeans 21
- JDK 17.0.12 (64-bit)
- GlassFish 7.0.23 — Jakarta EE 10 **Platform / Full Platform**
- MySQL Server 8.0.38
- MySQL Connector/J 9.7.0 (تنزيل منفصل)
- Node.js 26.4.0 وnpm

> **تنبيه التوافق:** المقرر لا يشترط Jakarta EE 11، لذلك يعتمد هذا الإصدار Jakarta EE 10. لا تخلط ملفات PostgreSQL التاريخية مع تثبيت MySQL الحالي.

## 0. قبل البدء

1. ضع مجلد المشروع `smart-hospital` في مسار ثابت، مثلاً `D:\Projects\smart-hospital`.
2. لا تشغّل ملفات SQL من `database/legacy-postgresql/migrations` أو `database/legacy-postgresql/seed`؛ هذه الملفات محفوظة كسجل تاريخي للتصميم السابق.
3. استخدم فقط ملفات MySQL الموجودة في `database/mysql`، ومن خلال سكربت التثبيت الموضح أدناه.
4. استخدم بيانات المشروع التجريبية فقط. لا تدخل بيانات مرضى حقيقية.

## 1. التأكد من NetBeans وJDK وGlassFish

1. افتح NetBeans 21.
2. من `Tools → Java Platforms` تأكد أن `JDK 17.0.12` مضاف.
3. من نافذة `Services` اختر `Servers → Add Server`.
4. أضف GlassFish 7.0.23 واختر مجلد تثبيته.
5. تأكد أن التوزيعة هي **Jakarta EE Platform 10 / Full Platform** وليست Web Profile؛ لأن التطبيق يستخدم EJB وJMS وMessage-Driven Beans.
6. شغّل `domain1` وتأكد أن خادم GlassFish يعمل محلياً.

## 2. تجهيز MySQL وإنشاء قاعدة المستشفى

1. افتح `Services` في Windows وتأكد أن خدمة MySQL 8.0.38 تعمل.
2. إذا كان مسار `mysql.exe` مختلفاً عن المسار الافتراضي، افتح `database/apply-mysql-local.ps1` وعدّل قيمة `MysqlBin`.
3. افتح PowerShell في مجلد المشروع الرئيسي.
4. نفّذ:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\database\apply-mysql-local.ps1
```

5. سيطلب `mysql_config_editor` كلمة مرور مستخدم MySQL الإداري، ويحفظ بيانات الدخول محلياً باسم `smart-hospital-local`.
6. ينشئ السكربت قاعدة `hospital` والجداول والقيود والمحفزات والبيانات التجريبية، ثم يشغّل استعلامات تحقق للقراءة فقط.

**تحذير مهم:** يفحص السكربت أولاً أن مخطط `hospital` لا يحتوي جداول أو عروضاً، ثم ينشئ قاعدة تطوير جديدة وفارغة باسم `hospital`. لا تستخدمه فوق قاعدة تحتوي بيانات مهمة، ولا تعتبره أداة لنقل البيانات من PostgreSQL. إذا فشل التثبيت في منتصف إنشاء قاعدة جديدة، لا تعاود تشغيل `apply-mysql-local.ps1` مباشرة؛ استخدم سكربت `database/reset-partial-install.ps1` بعد التأكد أن قاعدة `hospital` مخصصة لهذا التثبيت ولا تحتوي بيانات تحتاجها. هذا السكربت يحذف قاعدة `hospital` بالكامل ثم يعيد تثبيتها من الصفر بعد تأكيدك كتابة `RESET`.

## 3. إنشاء مستخدم MySQL مخصص للتطبيق

1. افتح `database/mysql/06_create_runtime_user.example.sql`.
2. استبدل كلمة المرور الوهمية `REPLACE_WITH_A_LONG_UNIQUE_PASSWORD` بكلمة مرور قوية خاصة بجهازك.
3. نفّذ الملف مرة واحدة من MySQL Workbench أو mysql client بحساب إداري.
4. ينشئ هذا الملف مستخدم التطبيق `smart_hospital_app` المسموح له بالاتصال من `127.0.0.1`، ويمنحه صلاحيات القراءة والإضافة والتحديث والحذف على قاعدة `hospital` فقط.
5. لا تشغّل التطبيق بحساب `root`، ولا تمنح حساب التطبيق صلاحيات `DROP` أو `ALTER` أو `CREATE` أو `TRIGGER` أو `GRANT OPTION`.

## 4. تثبيت MySQL Connector/J وإعداد GlassFish

1. نزّل MySQL Connector/J من الموقع الرسمي: <https://dev.mysql.com/downloads/connector/j/>.
2. احتفظ بملف JAR في مسار ثابت؛ مثال: `C:\mysql-connector\mysql-connector-j-9.7.0.jar`. استخدم اسم الملف الفعلي الذي نزلته.
3. افتح `glassfish/create-datasource.ps1` وعدّل:
   - `GlassFishHome`: مسار تثبيت GlassFish 7.0.23.
   - `MysqlConnectorJar`: المسار الكامل لملف Connector/J.
4. افتح PowerShell من مجلد المشروع، وشغّل:

```powershell
.\glassfish\create-datasource.ps1
```

أو مرّر المسارات مباشرة:

```powershell
.\glassfish\create-datasource.ps1 `
  -GlassFishHome 'C:\glassfish7' `
  -MysqlConnectorJar 'C:\mysql-connector\mysql-connector-j-9.7.0.jar'
```

5. ينسخ السكربت Driver إلى مكتبة `domain1` ويعيد تشغيل الخادم لتحميله.
6. سيطلب إنشاء GlassFish password alias. أدخل كلمة مرور مستخدم MySQL `smart_hospital_app` نفسها التي أنشأتها في الخطوة السابقة.
7. ينشئ XA Connection Pool ومصدر JNDI باسم `jdbc/SmartHospitalDS` ثم ينفذ `ping-connection-pool`.
8. لا تنتقل إلى نشر WAR قبل نجاح اختبار الاتصال.

إذا كانت هناك موارد قديمة بالاسم نفسه، احذف JDBC Resource أولاً ثم Connection Pool، واحذف password alias القديم عند الحاجة، ثم أعد السكربت.

## 5. حسابات المستخدمين التجريبية (قاعدة البيانات)

منذ الترحيل **V012** لم تعد المصادقة تعتمد على GlassFish File Realm. الحسابات وكلمات المرور المُجزّأة (PBKDF2-HMAC-SHA256، 210,000 تكرار) موجودة في `hospital.user_accounts`، ويُصدر التطبيق Bearer token عبر `POST /api/v1/auth/login`.

1. طبّق الترحيلات أولاً (القسم 4 أو `database/apply-migrations.ps1`). الترحيل V012 يضبط الحسابات التجريبية.
2. كلمة المرور الموحدة لبيئة العرض: **`Demo@2026`**، وكل الحسابات تحمل `must_change_password = TRUE`.
3. قبل أي استخدام إنتاجي: غيّر كل كلمات المرور عبر `POST /api/v1/auth/password` أو من الواجهة، وعطّل حسابات العرض غير المستخدمة، وأبطِل الجلسات القديمة.
4. لا حاجة لتشغيل `glassfish/create-demo-users.ps1`؛ فهو أثر تاريخي من إعداد File Realm السابق.

الحسابات اصطناعية للتطوير، مثل `hospitaladmin` و`manager1` و`receptionist1` و`doctor1` و`nurse1` و`labtech1` و`labsupervisor1` و`pharmacist1` و`accountant1` و`inventoryofficer1`.

مثال تحقق سريع:

```powershell
$token = (Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/api/v1/auth/login' `
  -ContentType 'application/json' `
  -Body '{"username":"hospitaladmin","password":"Demo@2026"}').token
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/security/me' `
  -Headers @{ Authorization = "Bearer $token" }
```

## 6. فتح المشروع وبناؤه من NetBeans

1. في NetBeans اختر `File → Open Project`.
2. اختر مجلد **`smart-hospital`** الذي يحتوي `pom.xml` الأب (وليس `smart-hospital-web` مباشرة).
3. ستظهر وحدات Maven الثلاث: المشروع الأب، `smart-hospital-web` (WAR)، `smart-hospital-ear` (EAR).
4. تأكد أن `pom.xml` يحدد Java release 17 واعتماد `jakarta.jakartaee-api:10.0.0` بنطاق `provided`.
5. من قائمة المشروع الأب اختر `Clean and Build`.
6. عند نجاح البناء ينتج الملف:

```text
smart-hospital-ear/target/smart-hospital.ear
```

7. انشر ملف EAR على GlassFish 7.0.23 (اسم التطبيق `smart-hospital` وContext Root `/`).
8. افتح سجل الخادم وتأكد من اكتمال النشر وعدم وجود أخطاء في JPA أو JMS أو JNDI.

> الواجهة مبنية داخل WAR من `frontend/dist`. أعد `npm run build` داخل `frontend` قبل بناء WAR إذا عدّلت ملفات React.
> لا تضف نسخة ثانية من Jakarta EE API أو EclipseLink إلى WAR؛ GlassFish يوفر هذه المكونات.

## 7. تشغيل واجهة React أثناء التطوير (اختياري)

التشغيل العادي لا يحتاجه لأن الواجهة مدمجة في EAR. أثناء تعديل الواجهة فقط:

```powershell
cd frontend
npm install
npm run dev
```

ثم افتح:

```text
http://localhost:5173
```

يستخدم Vite proxy لتوجيه `/api/*` مباشرة إلى التطبيق المنشور على:

```text
http://localhost:8080/api/*
```

ولأن Context Root أصبح `/`، لا توجد بادئة `smart-hospital-api` بعد الآن.

## 8. ما الذي يجب التأكد منه قبل اختبار القبول الشامل؟

- نجاح `Clean and Build` وإنتاج `smart-hospital.ear`.
- نجاح تطبيق سكربت MySQL على قاعدة تطوير فارغة.
- نجاح `ping-connection-pool` لمصدر `jdbc/SmartHospitalDS`.
- نجاح نشر EAR على GlassFish دون أخطاء.
- عمل `/api/v1/health` و`/api/v1/health/ready` بعد تسجيل الدخول.
- نجاح `npm run build` وتضمين الواجهة داخل WAR.
- نجاح تسجيل الدخول بحساب تجريبي وظهور القوائم المناسبة لدوره.

تم تنفيذ هذه الخطوات والتحقق منها فعلياً على بيئة Windows الحالية بتاريخ 2026-11-09. راجع `README.md` الرئيسي في جذر المشروع للتفاصيل الكاملة وأوامر إعادة البناء والنشر.

## ملاحظة سلامة وخصوصية

المشروع أكاديمي ببيانات اصطناعية وليس نظاماً طبياً معتمداً. لا تستخدمه لحفظ معلومات مرضى حقيقيين أو لاتخاذ قرارات علاجية فعلية.

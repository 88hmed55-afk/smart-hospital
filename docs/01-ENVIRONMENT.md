# 01 — تدقيق البيئة والإعداد التنفيذي

> **الغرض:** التأكد من أن كل الأدوات تعمل معاً قبل كتابة أول سطر كود، وحسم تعارض GlassFish 7/8.
> **قاعدة:** لا نبدأ Sprint 1 قبل نجاح "بوابة البيئة" في نهاية هذا الملف.

---

## 1. نتيجة التدقيق الفعلي للجهاز

| العنصر | المكتشف | الحالة |
|--------|---------|--------|
| نظام التشغيل | Windows (64-bit) | ✅ |
| JDK | `17.0.12` في `C:\Program Files\Java\jdk-17` | ✅ لكن `JAVA_HOME` غير مضبوط |
| NetBeans | `21` في `C:\Program Files\NetBeans-21\netbeans` | ✅ |
| Maven | داخل NetBeans: `...\java\maven\bin\mvn.cmd` | ⚠️ غير مضاف إلى PATH |
| GlassFish | **`7.0.23`** في `C:\server\glassfish-7.0.23\glassfish7` | ⚠️ **ليس 8.0.5** |
| Domain | `domain1` موجود | ✅ |
| إدارة GlassFish | المنفذ 4848 غير مستمع (الخادم متوقف) | ⚠️ يحتاج تشغيل |
| PostgreSQL | `16.6` خدمة `postgresql-x64-16` تعمل على 5432 | ✅ |
| مصادقة PostgreSQL | `scram-sha-256` | ✅ |
| psql | `C:\Program Files\PostgreSQL\16\bin\psql.exe` | ⚠️ غير مضاف إلى PATH |
| Node.js | `26.4.0` | ✅ |
| npm | `11.17.0` | ✅ |
| Git | `2.55.0` | ✅ |
| المساحة الحرة | ~190 GB | ✅ |

> **الخلاصة:** البيئة جاهزة تقنياً، لكن هناك **فجوة إصدار** بين البطاقة (EE 11/GF 8) والواقع (EE 10/GF 7). لا تبنِ على افتراض أن GF 8 مثبت.

---

## 2. قرار الإصدار: GlassFish 7 أم 8؟

### الخيارات

| المعيار | A) GlassFish 7.0.23 + Jakarta EE 10 | B) GlassFish 8.0.5 + Jakarta EE 11 |
|---------|--------------------------------------|-------------------------------------|
| متوفر الآن | ✅ | ❌ يحتاج تنزيلاً |
| تكامل NetBeans 21 | ✅ مدعوم | ⚠️ غير مضمون (NB21 أقدم من GF8) |
| يطابق بطاقة المشروع | ❌ | ✅ |
| مفاهيم المقرر | ✅ كلها موجودة | ✅ كلها موجودة |
| المخاطرة | منخفضة | متوسطة |
| الترقية لاحقاً | سهلة نسبياً | — |

### التوصية الاحترافية

**اعمل على A أثناء التطوير، واجعل B بوابة ترقية في الأسبوع الثاني.**

السبب: الفرق بين EE 10 وEE 11 في واجهات المشروع ضئيل جداً (لا تستخدم أي API محذوف). تطويرك على GF 7 يمنحك تصحيحاً وتنصيباً مباشراً من NetBeans 21، ثم:

1. نزّل GF 8.0.5 إلى `C:\server\glassfish-8.0.5`.
2. غيّر تبعية API إلى `jakarta.jakartaee-api:11.0.0` (إن أردت).
3. انشر نفس الـ EAR على GF 8.
4. إن نجح: وثّق "تم الاختبار على Jakarta EE 11 / GlassFish 8" واستخدمه للعرض.
5. إن فشل تكامل NetBeans: أبقِ التطوير على GF 7، وانشر على GF 8 عبر `asadmin` أو Maven في مرحلة التسليم.

> **لا تضيّع أسبوعاً في محاولة جعل NetBeans 21 يعرف GF 8.** البرنامج الأكاديمي يُقيّم النظام، لا أداة IDE. وجود دليل نشر ناجح على GF 8 يكفي.

### إن أصرّ المشرف على EE 11 من اليوم الأول

- نزّل **JDK 21** (Temurin) وثبّته بجانب JDK 17.
- نزّل **GlassFish 8.0.5** (ملف ZIP) وفكّه في `C:\server\glassfish-8.0.5`.
- سجّل الخادم يدوياً في NetBeans (Tools → Servers → Add Server → GlassFish Server → مسار GF8).
- إن رفض NetBeans الإصدار: استخدم **NetBeans 29+** أو انشر بـ `asadmin deploy` من سكربت Maven.
- استخدم `jakarta.jakartaee-api:11.0.0` و`maven-compiler-plugin` بـ `release=17` (أو 21).
- تأكد أن كل الاستيرادات `jakarta.*` وليست `javax.*` (عدا `javax.sql.DataSource` و`javax.crypto`).

---

## 3. ضبط متغيرات البيئة (PowerShell)

نفّذ مرة واحدة، ثم افتح طرفية جديدة:

```powershell
# JAVA_HOME
[Environment]::SetEnvironmentVariable(
  'JAVA_HOME',
  'C:\Program Files\Java\jdk-17',
  'User'
)

# أدوات سطر الأوامر
$maven  = 'C:\Program Files\NetBeans-21\netbeans\java\maven\bin'
$pg     = 'C:\Program Files\PostgreSQL\16\bin'
$gf     = 'C:\server\glassfish-7.0.23\glassfish7\bin'

$userPath = [Environment]::GetEnvironmentVariable('Path','User')
foreach ($p in @($maven,$pg,$gf)) {
  if ($userPath -notlike "*$p*") { $userPath = "$userPath;$p" }
}
[Environment]::SetEnvironmentVariable('Path', $userPath, 'User')
```

تحقق:

```powershell
java -version
mvn -version
psql --version
asadmin version
```

> إن لم يعمل `mvn` بعد فتح طرفية جديدة، أعد تسجيل الدخول إلى Windows أو استخدم المسار الكامل:
> `& 'C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd' -version`

---

## 4. إعداد PostgreSQL

### 4.1 إنشاء المستخدم وقواعد البيانات

افتح PowerShell كمدير أو استخدم `psql` مع مستخدم `postgres` (سيُطلب كلمة المرور):

```powershell
$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'

& $psql -U postgres -h localhost -c "CREATE ROLE smart_hospital LOGIN PASSWORD 'ChangeMe_Strong_2026!';"
& $psql -U postgres -h localhost -c "CREATE DATABASE smart_hospital OWNER smart_hospital ENCODING 'UTF8' TEMPLATE template0;"
& $psql -U postgres -h localhost -c "CREATE DATABASE smart_hospital_test OWNER smart_hospital ENCODING 'UTF8' TEMPLATE template0;"
```

### 4.2 الامتدادات والمخطط

```powershell
& $psql -U postgres -h localhost -d smart_hospital -c @"
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE SCHEMA IF NOT EXISTS app AUTHORIZATION smart_hospital;
"@

& $psql -U postgres -h localhost -d smart_hospital -c "ALTER ROLE smart_hospital IN DATABASE smart_hospital SET search_path = app, public;"
& $psql -U postgres -h localhost -d smart_hospital_test -c "ALTER ROLE smart_hospital IN DATABASE smart_hospital_test SET search_path = app, public;"
```

> **لماذا `btree_gist`؟** لأنه يسمح بدمج `=` مع `&&` في قيد `EXCLUDE`، وهو ما يمنع تعارض المواعيد والأسرّة. بدون الامتداد ستفشل القيود.

### 4.3 التحقق

```powershell
& $psql -U smart_hospital -h localhost -d smart_hospital -c "select current_database(), current_user, current_schema();"
```

يجب أن يظهر `smart_hospital` و`app`.

### 4.4 أدوات مساعدة

- `pgAdmin 4` مثبت مع PostgreSQL — استخدمه لاستعراض المخطط.
- أنشئ ملف `.pgpass` أو استخدم متغير `PGPASSWORD` في السكربتات المحلية فقط، ولا ترفعه إلى Git.

---

## 5. إعداد GlassFish 7.0.23

### 5.1 التشغيل وتعيين كلمة مرور الإدارة

```powershell
$gf = 'C:\server\glassfish-7.0.23\glassfish7'
& "$gf\bin\asadmin.bat" start-domain domain1
& "$gf\bin\asadmin.bat" --user admin change-admin-password
& "$gf\bin\asadmin.bat" enable-secure-admin
& "$gf\bin\asadmin.bat" restart-domain domain1
```

- كلمة المرور القديمة غالباً فارغة (اضغط Enter).
- عيّن كلمة مرور قوية للإدارة واحفظها في مدير كلمات مرور.
- تحقق: `http://localhost:4848` و`http://localhost:8080`.

### 5.2 سائق PostgreSQL

GlassFish يحتاج سائق JDBC. أنزله إلى `domain1/lib`:

```powershell
$mvn = 'C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd'
$lib = 'C:\server\glassfish-7.0.23\glassfish7\glassfish\domains\domain1\lib'

& $mvn org.apache.maven.plugins:maven-dependency-plugin:3.7.1:copy `
  -Dartifact=org.postgresql:postgresql:42.7.7 `
  -DoutputDirectory=$lib
```

أو نزّل `postgresql-42.7.7.jar` من `jdbc.postgresql.org` وانسخه يدوياً.

> أعد تشغيل GlassFish بعد نسخ السائق.

### 5.3 Connection Pool وJNDI

```powershell
$asadmin = "$gf\bin\asadmin.bat"

& $asadmin create-jdbc-connection-pool `
  --datasourceclassname org.postgresql.ds.PGSimpleDataSource `
  --restype javax.sql.DataSource `
  --property "user=smart_hospital:password=ChangeMe_Strong_2026!:databaseName=smart_hospital:serverName=localhost:portNumber=5432" `
  hospitalPool

& $asadmin create-jdbc-resource --connectionpoolid hospitalPool jdbc/hospitalDS
& $asadmin ping-connection-pool hospitalPool
```

في الإنتاج، لا تضع كلمة المرور نصاً:

```powershell
& $asadmin create-password-alias hospital-db-password
# ثم استخدم في الخاصية:
# password=${ALIAS=hospital-db-password}
```

### 5.4 JMS Queue وConnection Factory

```powershell
& $asadmin create-jms-resource --restype jakarta.jms.ConnectionFactory jms/HospitalConnectionFactory
& $asadmin create-jms-resource --restype jakarta.jms.Queue --property Name=NotificationQueue jms/NotificationQueue
& $asadmin create-jms-resource --restype jakarta.jms.Queue --property Name=NotificationDLQ jms/NotificationDLQ
& $asadmin list-jms-resources
```

### 5.5 التحقق من الموارد

```powershell
& $asadmin list-jdbc-connection-pools
& $asadmin list-jdbc-resources
& $asadmin list-jms-resources
```

---

## 6. تسجيل GlassFish في NetBeans 21

1. `Tools` → `Servers` → `Add Server`.
2. اختر `GlassFish Server` → `Next`.
3. Installation Location: `C:\server\glassfish-7.0.23\glassfish7`.
4. اختر `domain1` (أو أنشئ `domain1` جديداً).
5. أدخل مستخدم/كلمة مرور الإدارة.
6. `Finish` ثم تأكد من ظهور الخادم و`Start/Stop` يعملان.

### إن لم يظهر GlassFish في القائمة

- `Tools` → `Plugins` → `Available Plugins` → ابحث عن `GlassFish` → ثبّت.
- أو استخدم `Java EE`/`Jakarta EE` base من NetBeans 21 (عادة مضمّن).
- تأكد أن NetBeans يعمل على JDK 17 (Help → About).

---

## 7. إنشاء المشروع في NetBeans (Maven Enterprise Application)

### 7.1 الطريقة الموصى بها

1. `File` → `New Project` → **Java with Maven** → **Enterprise Application**.
2. الاسم: `smarthospital`، والمجلد: `C:\Users\ZBook\Desktop\مجلد جديد\smart-hospital`.
3. اختر خادم GlassFish وإصدار Java EE/Jakarta EE المناسب (EE 10 مع GF7).
4. سينشئ NetBeans:
   - `smarthospital` (EAR)
   - `smarthospital-web` (WAR)
5. أضف وحدتين:
   - `New Project` → `Java with Maven` → `EJB Module` → `smarthospital-ejb`.
   - `New Project` → `Java with Maven` → `Java Class Library` → `smarthospital-shared`.
6. أضف الوحدتين إلى الـ EAR من `smarthospital/pom.xml` (قسم `maven-ear-plugin`) وأضف التبعيات.
7. افتح `smarthospital` كـ Main Project، ثم `Run` (Deploy).

> إن لم يوفّر NetBeans خيار "Enterprise Application" تحت Maven، أنشئ `Maven Project` عادياً ثم عدّل `pom.xml` إلى `ear`، أو استخدم هيكل الـ Parent الموصوف في `02-ARCHITECTURE-DATA.md`.

### 7.2 إعدادات المشروع

| الإعداد | القيمة |
|---------|--------|
| Java Platform | JDK 17 |
| Source/Binary | 17 |
| Server | GlassFish 7.0.23 / domain1 |
| Jakarta EE API | 10.0.0 (أو 11.0.0 مع GF8) |
| Context Root | `/` (يفضل لخدمة React وAPI من نفس الأصل) |
| Database | `jdbc/hospitalDS` |
| JMS | `jms/NotificationQueue` |

### 7.3 التحقق من النشر

بعد `Run`، افتح:
- `http://localhost:8080/` → يجب أن تظهر صفحة React أو صفحة ترحيب.
- `http://localhost:8080/api/v1/health` → `{ "status": "UP" }`.

إن ظهرت 404، تحقق من `application.xml` و`context-root` و`web.xml`.

---

## 8. إعداد React (Vite + TypeScript)

```powershell
cd 'C:\Users\ZBook\Desktop\مجلد جديد\smart-hospital'
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm install @tanstack/react-query react-router-dom react-hook-form zod axios
npm install -D tailwindcss @tailwindcss/vite
npm run dev
```

### وضع التطوير

- Vite على `http://localhost:5173`.
- Proxy في `vite.config.ts`:

```ts
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```

بهذا لا تحتاج CORS إطلاقاً: المتصفح يرى نفس الأصل (5173).

### وضع الإنتاج

- `npm run build` ينتج `dist/`.
- انسخ `dist/*` إلى `smarthospital-web/src/main/webapp/` (أو استخدم `maven-resources-plugin`).
- الـ WAR يخدم `index.html` وAPI من نفس الأصل `/api/v1`.

---

## 9. الهيكل النهائي المقترح

```text
smart-hospital/
├─ pom.xml                     # parent
├─ shared/                     # JAR: DTOs, enums, errors
├─ ejb/                        # EJB JAR: entities, services, MDB, schedulers
├─ web/                        # WAR: JAX-RS, filters, React build
├─ ear/                        # EAR: packaging
├─ frontend/                   # React + TS + Vite
├─ db/migration/               # Flyway V1..Vn
├─ db/seed/                    # بيانات اصطناعية
├─ scripts/                    # setup, deploy, backup, demo-reset
├─ docs/                       # هذه الحزمة
└─ .github/workflows/ci.yml    # بناء واختبار
```

---

## 10. بوابة البيئة (Environment Gate)

لا تنتقل إلى Sprint 1 حتى تتحقق كل هذه البنود:

- [ ] `java -version` يعرض 17.
- [ ] `mvn -version` يعمل من طرفية جديدة.
- [ ] PostgreSQL يعمل، وقاعدة `smart_hospital` موجودة، و`current_schema()` = `app`.
- [ ] امتدادات `btree_gist` و`pg_trgm` و`citext` مثبتة.
- [ ] GlassFish يعمل، ولوحة 4848 تفتح.
- [ ] `ping-connection-pool hospitalPool` ناجح.
- [ ] `jdbc/hospitalDS` و`jms/NotificationQueue` موجودان.
- [ ] NetBeans يعرض GlassFish ويستطيع Start/Stop.
- [ ] مشروع EAR ينشر بنجاح.
- [ ] `GET /api/v1/health` يعيد 200.
- [ ] React يعمل على 5173 ويصل إلى `/api/v1/health` عبر Proxy.
- [ ] `git init` و`.gitignore` يمنعان `target/`, `node_modules/`, `.env`, `*.log`, `dist/`.

---

## 11. استكشاف الأخطاء

| المشكلة | السبب المحتمل | الحل |
|---------|----------------|------|
| `mvn` غير معروف | PATH لم يُحدّث | افتح طرفية جديدة أو استخدم المسار الكامل |
| GlassFish لا يبدأ | منفذ مشغول أو JDK خاطئ | `netstat -ano | findstr :4848`، وتأكد `AS_JAVA`/JDK 17 |
| `ClassNotFoundException: org.postgresql.Driver` | السائق غير موجود | انسخه إلى `domain1/lib` وأعد التشغيل |
| `Connection refused` | PostgreSQL متوقف | `Get-Service postgresql-x64-16` ثم Start |
| `password authentication failed` | كلمة مرور/مستخدم خاطئ | أعد إنشاء الدور أو اختبر بـ psql |
| `extension "btree_gist" is not available` | الامتداد غير مثبت | نفّذ `CREATE EXTENSION` كمستخدم superuser |
| `EXCLUDE constraint` يفشل | `btree_gist` ناقص أو أنواع الأعمدة غير متوافقة | ثبّت الامتداد واستخدم `tstzrange` |
| NetBeans لا يتعرف على GF8 | إصدار أحدث من دعم IDE | استخدم GF7 أو انشر بـ asadmin |
| 404 على API | Context root خاطئ | اضبط `/` في `application.xml` و`glassfish-web.xml` |
| Cookie لا يُحفظ | `Secure` على HTTP | عطّل `Secure` في profile التطوير فقط |
| JMS Queue غير موجود | الاسم الفيزيائي خاطئ | أضف `--property Name=NotificationQueue` |

### سجلات مفيدة

```powershell
Get-Content 'C:\server\glassfish-7.0.23\glassfish7\glassfish\domains\domain1\logs\server.log' -Tail 200
Get-Content 'C:\Program Files\PostgreSQL\16\data\log\*.log' -Tail 100
```

---

## 12. النسخ الاحتياطي والاستعادة (من اليوم الأول)

```powershell
# نسخة
& 'C:\Program Files\PostgreSQL\16\bin\pg_dump.exe' `
  -U smart_hospital -h localhost -Fc -f "backup\smart_hospital_$(Get-Date -Format yyyyMMdd_HHmm).dump" smart_hospital

# استعادة إلى قاعدة اختبار
& 'C:\Program Files\PostgreSQL\16\bin\pg_restore.exe' `
  -U smart_hospital -h localhost -d smart_hospital_test --clean --if-exists backup\smart_hospital_YYYYMMDD_HHmm.dump
```

- شفّر النسخ في الإنتاج (مثلاً GPG أو BitLocker).
- نفّذ **تجربة استعادة فعلية** مرة قبل التسليم، ووثّق الزمن (RTO) وحجم الفقد (RPO).
- لا تضع نسخاً حقيقية أو بيانات مرضى في Git.

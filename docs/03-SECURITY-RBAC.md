# 03 — الأمان والصلاحيات (IAM / RBAC / ABAC)

> **المبدأ:** الدور يحدد **نوع** العمل. العلاقة بالبيانات تحدد **أي** سجل. السياق يحدد **هل** يُسمح الآن.
> **القاعدة:** كل تحقق يُنفّذ في الخادم. إخفاء الزر في React ليس صلاحية.

---

## 1. نموذج الأمان في ثلاث طبقات

```text
1) Authentication  → من أنت؟        (جلسة + كلمة مرور + قفل محاولات)
2) RBAC            → ما نوع عملك؟    (دور + صلاحية ذرية)
3) ABAC            → أي سجل بالتحديد؟ (علاقة طبيب-مريض، قسم، طلب، إقامة)
   + Break-glass   → استثناء طارئ مبرر ومؤقت ومسجّل
```

كل طلب يمر بـ:

```text
Cookie → AuthFilter → Session → User + Roles + Permissions
      → Resource (@RolesAllowed)
      → Service (@RequiresPermission)
      → Repository (AccessScope)
      → Database
      → Audit (قراءة/كتابة/رفض)
```

---

## 2. الأدوار

| الدور | الغرض | ملاحظات |
|-------|-------|---------|
| `SYSTEM_ADMIN` | إعداد النظام والحسابات | لا يرى السجل الطبي افتراضياً |
| `HOSPITAL_MANAGER` | إشراف وتقارير تشغيلية | يرى مجاميع، وتفاصيل تشغيلية بلا ملاحظات سريرية |
| `RECEPTIONIST` | مرضى ومواعيد واستقبال | ديموغرافيا فقط |
| `DOCTOR` | زيارة وتشخيص وطلبات | يرى مرضاه المرتبطين به فقط |
| `NURSE` | علامات حيوية وملاحظات | حسب القسم/السرير/التriage |
| `LAB_TECHNICIAN` | عينات وإدخال نتائج | لا يعتمد نتائجه |
| `LAB_SUPERVISOR` | اعتماد النتائج | فصل واجبات صارم |
| `PHARMACIST` | وصفات وصرف ومخزون | يرى الحساسية والتفاعلات فقط |
| `ACCOUNTANT` | فواتير ومدفوعات | لا سجل سريري |
| `INVENTORY_OFFICER` | مخزون وموردون | لا بيانات مرضى |
| `AUDITOR` | تدقيق وصول وتغيير | قراءة فقط، بيانات حساسة مقنّعة |

> يمكن لاحقاً إضافة `INSURANCE_OFFICER` و`PATIENT` بدون تغيير النواة.

---

## 3. كتالوج الصلاحيات (Permissions)

| المجال | الصلاحيات |
|--------|-----------|
| المرضى | `patient.read`, `patient.create`, `patient.update`, `patient.merge`, `patient.export` |
| المواعيد | `appointment.read`, `appointment.create`, `appointment.reschedule`, `appointment.cancel`, `appointment.checkin`, `queue.manage` |
| الزيارة | `encounter.read`, `encounter.create`, `encounter.close`, `vitals.write`, `note.write`, `diagnosis.write` |
| الوصفات | `prescription.read`, `prescription.create`, `prescription.approve`, `prescription.dispense`, `prescription.cancel` |
| المختبر | `lab.order.create`, `lab.specimen.collect`, `lab.result.enter`, `lab.result.approve`, `lab.critical.ack`, `lab.test.manage` |
| الصيدلية | `drug.read`, `drug.manage`, `stock.read`, `stock.adjust`, `batch.manage`, `stock.alert.manage` |
| المال | `invoice.read`, `invoice.create`, `invoice.void`, `payment.create`, `payment.void`, `discount.approve`, `ledger.read`, `finance.report` |
| التنويم | `bed.read`, `bed.assign`, `admission.create`, `admission.transfer`, `admission.discharge` |
| التحليلات | `analytics.view`, `analytics.export`, `decisioncard.view`, `decisioncard.manage`, `report.run` |
| الأمان | `user.manage`, `role.manage`, `config.manage`, `audit.view`, `audit.verify`, `breakglass.use` |

> الصلاحية تُخزَّن بكود ثابت، وتُستخدم في الكود كـ `@RequiresPermission(Permission.LAB_RESULT_APPROVE)`. لا نصوص حرة.

---

## 4. مصفوفة الأدوار × الصلاحيات (مختصرة)

| الصلاحية | Admin | Manager | Reception | Doctor | Nurse | Lab Tech | Lab Sup | Pharmacist | Accountant | Inventory | Auditor |
|----------|:-----:|:-------:|:---------:|:------:|:-----:|:--------:|:-------:|:----------:|:----------:|:---------:|:-------:|
| patient.read | — | △ | ✅ | △ | △ | △ | △ | △ | △ | — | — |
| patient.create/update | — | — | ✅ | — | — | — | — | — | — | — | — |
| patient.merge | ✅ | — | — | — | — | — | — | — | — | — | — |
| appointment.read | — | ✅ | ✅ | △ | △ | — | — | — | — | — | — |
| appointment.create | — | — | ✅ | — | — | — | — | — | — | — | — |
| appointment.checkin | — | — | ✅ | — | — | — | — | — | — | — | — |
| encounter.read | — | — | — | △ | △ | △ | △ | △ | — | — | — |
| encounter.create | — | — | — | ✅ | — | — | — | — | — | — | — |
| vitals.write | — | — | — | ✅ | ✅ | — | — | — | — | — | — |
| diagnosis.write | — | — | — | ✅ | — | — | — | — | — | — | — |
| prescription.create | — | — | — | ✅ | — | — | — | — | — | — | — |
| prescription.dispense | — | — | — | — | — | — | — | ✅ | — | — | — |
| lab.order.create | — | — | — | ✅ | — | — | — | — | — | — | — |
| lab.result.enter | — | — | — | — | — | ✅ | ✅ | — | — | — | — |
| lab.result.approve | — | — | — | — | — | — | ✅ | — | — | — | — |
| invoice.read | — | △ | — | — | — | — | — | — | ✅ | — | — |
| payment.create | — | — | — | — | — | — | — | — | ✅ | — | — |
| discount.approve | — | ✅ | — | — | — | — | — | — | △ | — | — |
| stock.read/adjust | — | — | — | — | — | — | — | ✅ | — | ✅ | — |
| bed.assign/admission | — | — | — | △ | △ | — | — | — | — | — | — |
| analytics.view | — | ✅ | — | — | — | — | — | — | △ | — | — |
| decisioncard.manage | — | ✅ | — | — | — | — | — | — | — | — | — |
| audit.view/verify | — | — | — | — | — | — | — | — | — | — | ✅ |
| user.manage | ✅ | — | — | — | — | — | — | — | — | — | — |

**الرموز:** ✅ صلاحية كاملة، △ مشروطة بـ ABAC (انظر القسم 5)، — ممنوعة.

> المصفوفة الكاملة تُخزَّن في `role_permission` وتُختبر آلياً. لا تعتمد على هذه الجدولة الورقية وحدها.

---

## 5. ABAC — قواعد الوصول حسب العلاقة

### 5.1 الطبيب

**يرى المريض إذا تحقق أحد الشروط:**
- لديه `encounter` مع المريض (مفتوحة أو مغلقة).
- لديه `appointment` مع المريض (غير ملغى) خلال نافذة زمنية معقولة.
- لديه `lab_order` أو `prescription` أنشأها لهذا المريض.
- المريض في قسمه ولديه تكليف مناوبة فعّال (مع تسجيل سبب).
- `break-glass` فعّال.

**لا يرى:** كل مرضى المستشفى، بيانات مالية، تدقيق.

**يكتب:** زياراته فقط، وتشخيصاته، وطلباته. لا يعدل زيارة مغلقة أو موقعة.

### 5.2 الممرضة

- ترى المريض إذا كان في قسمها/سريرها أو في طابور triage المخصص لها.
- تكتب علامات حيوية وملاحظات تمريضية.
- لا تعتمد تشخيصاً نهائياً ولا نتيجة مختبر.
- ترى تنبيهات الحساسية والتفاعلات (للسلامة) دون كامل السجل المالي.

### 5.3 الاستقبال

- يرى الاسم، MRN، الهاتف، الموعد، الحالة، الدفع المستحق.
- لا يرى الملاحظات السريرية، النتائج، التشخيصات.
- يستطيع تسجيل مريض جديد، حجز، إلغاء، تسجيل وصول.
- لا يستطيع تعديل وصفة أو اعتماد نتيجة.

### 5.4 المختبر

- `LAB_TECHNICIAN`: يرى الطلبات المعينة لقسمه/دوره، يسجل العينة والنتيجة.
- `LAB_SUPERVISOR`: يعتمد النتائج. **لا يعتمد نتيجة أدخلها بنفسه** (فصل واجبات).
- كلاهما لا يرى السجل المالي أو ملاحظات الأطباء غير المتعلقة بالطلب.

### 5.5 الصيدلي

- يرى الوصفات المعتمدة، الحساسية، قائمة الأدوية الحالية، والمخزون.
- لا يرى التشخيص الكامل أو الملاحظات السريرية (إلا تنبيه تفاعل).
- لا يعدل الفاتورة؛ ينشئ بند صرف فقط.

### 5.6 المحاسب

- يرى الفواتير، البنود، المدفوعات، اسم المريض وMRN.
- لا يرى التشخيصات أو النتائج أو الملاحظات.
- لا يعدل وصفة أو صرفاً.
- الخصم فوق حد معين يتطلب موافقة مدير (Segregation of Duties).

### 5.7 المدير

- يرى مؤشرات مجمعة وتفاصيل تشغيلية (أقسام، أطباء، أزمنة).
- لا يرى ملاحظات سريرية أو نتائج فردية إلا بضرورة إدارية موثقة.
- يعتمد خطط بطاقات القرار، ولا ينفذ تغييراً طبياً.

### 5.8 المدقق

- يرى `audit_event` وسجل الوصول، مع إخفاء الحقول السريرية.
- لا يعدل شيئاً. لا يرى السجل الطبي الكامل.
- يستطيع التحقق من سلسلة Hash وتصدير تقرير.

### 5.9 مدير النظام

- يدير المستخدمين والأدوار والإعدادات.
- **لا يمنح نفسه** وصولاً سريرياً. أي محاولة تُسجَّل وتتطلب تفويضاً منفصلاً.
- لا يرى كلمات المرور (Hash فقط).

### 5.10 التنفيذ البرمجي

```java
public interface PatientAccessPolicy {
  boolean canRead(CurrentUser user, Patient patient);
  boolean canReadClinical(CurrentUser user, Patient patient);
}
```

- Repository يستقبل `AccessScope` ويبني شرط `EXISTS` في SQL، بدلاً من جلب كل المرضى ثم الفلترة.
- `PatientResource.get(id)` يستدعي `accessPolicy.canRead` ثم `canReadClinical` لتحديد الحقول.
- أي رفض يكتب Security Audit (`REQUIRES_NEW`) برمز `ACCESS_DENIED`.

---

## 6. Break-glass (الوصول الطارئ)

### السيناريو

طبيب مناوب يحتاج سجل مريض غير مرتبط به في حالة طارئة.

### الضوابط

1. `POST /api/v1/security/break-glass` مع `patientId`, `reason` (10–500 حرف), `durationMinutes` (5–60).
2. إعادة إدخال كلمة المرور (خطوة ثانية) أو MFA إن لزم.
3. إنشاء `break_glass_grant` + إشعار فوري للمدير والمدقق.
4. شريط أحمر ثابت في الواجهة طوال المدة + مؤقت.
5. كل قراءة خلال المدة تُسجَّل بوسم `BREAK_GLASS`.
6. انتهاء تلقائي، وإمكانية إبطال يدوي.
7. استخدام متكرر أو خارج الطوارئ → بطاقة قرار أمنية.

```sql
CREATE TABLE app.break_glass_grant (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id         bigint NOT NULL REFERENCES app.app_user(id),
  patient_id      bigint NOT NULL REFERENCES app.patient(id),
  reason          text NOT NULL CHECK (length(reason) BETWEEN 10 AND 500),
  granted_at      timestamptz NOT NULL DEFAULT now(),
  expires_at      timestamptz NOT NULL,
  revoked_at      timestamptz,
  revoked_by      bigint REFERENCES app.app_user(id),
  CHECK (expires_at > granted_at),
  CHECK (expires_at <= granted_at + interval '60 minutes')
);
```

> **ليس بديلاً عن الصلاحية:** هو استثناء مؤقت، لا دور دائم. لا يُمنح للمحاسب أو المدقق.

---

## 7. المصادقة والجلسات

### 7.1 كلمات المرور

| العنصر | القرار |
|--------|--------|
| الخوارزمية | **Argon2id** (مكتبة `de.mkammerer:argon2-jvm`) |
| المعاملات | `m=65536 KB`, `t=3`, `p=4` (قابلة للترقية) |
| Salt | عشوائي 16 بايت لكل كلمة مرور (داخل التجزئة) |
| التخزين | `password_hash` + `password_algo` + `password_params` |
| إعادة التجزئة | عند الدخول إذا تغيرت المعاملات |
| السياسة | طول 12+، لا قواعد تركيب معقدة، رفض الكلمات الشائعة/المسربة محلياً |
| ممنوع | MD5/SHA1/تشفير مخصص/نص عادي/`base64` |
| البديل | PBKDF2-HMAC-SHA256 بـ 600,000 تكرار إن لم تتوفر Argon2 |

> **تصحيح مهم لبطاقة المشروع:** "خوارزمية مخصصة لكلمات المرور" عبارة خطرة. لا تصمم تشفيراً. استخدم KDF قياسياً.

### 7.2 الجلسة

| العنصر | القرار |
|--------|--------|
| النوع | Opaque token عشوائي 256-bit |
| التخزين | `user_session.token_hash = SHA-256(token)` |
| Cookie | `SH_SESSION`، `HttpOnly`, `SameSite=Strict`, `Secure` في الإنتاج، `Path=/` |
| المدة | خمول 30 دقيقة، حد أقصى 8 ساعات |
| التدوير | عند الدخول وتغيير الصلاحية/كلمة المرور |
| الإبطال | تسجيل خروج، تغيير كلمة مرور، تعطيل حساب، "خروج من كل الأجهزة" |
| التزامن | حد أقصى 3 جلسات نشطة (اختياري) |

**لماذا ليس JWT في LocalStorage؟** لأن الإبطال الفوري مهم في نظام صحي، وJWT في المتصفح عرضة لـ XSS. جلسة Opaque قابلة للإبطال ومراجعة.

### 7.3 CSRF

- `SameSite=Strict` خط الدفاع الأول.
- Token عشوائي 32 بايت لكل جلسة، يُخزَّن في `user_session.csrf_token`.
- يُرسل في Cookie قابل للقراءة `SH_CSRF` + Header `X-CSRF-Token`.
- كل `POST/PUT/PATCH/DELETE` يتحقق من التطابق.
- `GET` لا يغيّر حالة.
- تسجيل الخروج `POST` وليس رابطاً.

### 7.4 محاولات الدخول

- 5 محاولات فاشلة خلال 15 دقيقة → قفل 15 دقيقة (متزايد).
- تسجيل كل محاولة (`login_attempt`) مع IP/User-Agent.
- رسالة موحدة: "بيانات الدخول غير صحيحة" (لا تكشف وجود المستخدم).
- فتح الحساب بواسطة `SYSTEM_ADMIN` مع تدقيق.
- Rate limiting على `/auth/login` و`/security/break-glass`.

### 7.5 تغيير كلمة المرور

- المستخدم يغيّر كلمته بعد التحقق من القديمة.
- `must_change_password` للحسابات الجديدة/المعاد تعيينها.
- تغيير كلمة المرور يبطل كل الجلسات الأخرى.
- لا تُسجَّل كلمة المرور في السجلات أبداً.

### 7.6 HTTPS

- الإنتاج: HTTPS فقط + HSTS (`max-age=31536000; includeSubDomains`).
- محلياً: HTTP مسموح، مع `Secure=false` في profile التطوير فقط.
- لا تُرسل Cookies عبر HTTP في الإنتاج.
- شهادة ذاتية للتجربة المحلية موثقة، وليست حلاً للإنتاج.

---

## 8. سجل التدقيق (Audit)

### 8.1 ما يُسجَّل؟

| الفئة | أمثلة | التوقيت |
|-------|-------|---------|
| مصادقة | دخول ناجح/فاشل، خروج، قفل | فوري |
| وصول مرفوض | 403، محاولة سجل غير مصرح، Break-glass | `REQUIRES_NEW` |
| تغيير سريري | زيارة، تشخيص، وصفة، نتيجة، اعتماد | نفس المعاملة |
| تغيير مالي | فاتورة، دفعة، إلغاء، خصم | نفس المعاملة |
| تغيير مخزون | صرف، استلام، تسوية | نفس المعاملة |
| قراءة حساسة | فتح سجل طبي، تصدير، طباعة | بعد القراءة |
| إدارة | مستخدم/دور/إعداد | نفس المعاملة |
| أمني | Break-glass، تعطيل حساب، فتح قفل | `REQUIRES_NEW` |

### 8.2 بنية الحدث

```sql
CREATE TABLE app.audit_event (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  event_id       uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  occurred_at    timestamptz NOT NULL DEFAULT now(),
  actor_user_id  bigint REFERENCES app.app_user(id),
  actor_username text NOT NULL,
  action         text NOT NULL,
  entity_type    text NOT NULL,
  entity_id      text,
  patient_id     bigint,
  outcome        text NOT NULL CHECK (outcome IN ('SUCCESS','DENIED','FAILURE')),
  severity       text NOT NULL DEFAULT 'INFO',
  reason         text,
  ip             inet,
  user_agent     text,
  trace_id       text NOT NULL,
  before_data    jsonb,
  after_data     jsonb,
  prev_hash      bytea NOT NULL,
  hash           bytea NOT NULL
);
```

### 8.3 سلسلة Hash (Tamper-evident)

```text
hash_i = SHA-256( hash_{i-1} || canonical_json(event_i) )
hash_0 = 64 صفراً
```

- يُخزَّن `prev_hash` و`hash` في كل صف.
- مهمة أسبوعية تتحقق من السلسلة وتفتح بطاقة قرار عند كسر.
- المدقق يرى زر "تحقق من السلامة" + نتيجة.
- لا يمنع الحذف والمنع المطلق (المدير الفائق يستطيع)، لكنه **يكشف** التلاعب.

> ⚠️ **تحدٍّ حقيقي:** سلسلة Hash واحدة تعني أن كل كتابة تدقيق تحتاج معرفة Hash السابق. تحت التزامن، خيطان قد يقرآن نفس `prev_hash` فتتفرع السلسلة.
> الحلول:
> 1. **رأس سلسلة بقفل:** `audit_chain_head` صف واحد + `SELECT ... FOR UPDATE` أو `pg_advisory_xact_lock(hashtext('audit_chain'))`. بسيط وصحيح، لكنه يسلْسل كتابات التدقيق.
> 2. **سلسلة لكل كيان/مريض** (`entity_type + entity_id`) + **مرساة يومية** تربط السلاسل. توازٍ أفضل، وتعقيد أعلى.
> 3. **Merkle root دوري** كل دقيقة/ساعة + تحقق لاحق.
>
> للنسخة الأكاديمية: ابدأ بالحل (1) واذكره صراحة كقيد أداء، ثم اقترح (2) كتوسع. لا تدّعِ أن السلسلة آمنة تحت التزامن دون قفل.

### 8.4 منع التعديل

- Trigger `deny_mutation` على `audit_event` و`stock_movement`.
- لا `UPDATE/DELETE` من التطبيق.
- صلاحيات DB: `SELECT, INSERT` فقط لمستخدم التطبيق على هذه الجداول.
- الاحتفاظ: 7 سنوات للتدقيق (سياسة أكاديمية)، مع أرشفة وتقسيم.

---

## 9. إخفاء البيانات (Masking)

| السياق | ما يُخفى |
|--------|----------|
| الاستقبال | الملاحظات السريرية، النتائج، التشخيصات |
| المحاسب | كل المحتوى السريري |
| المدقق | محتوى `before/after` السريري → `[REDACTED]` |
| السجلات | لا أسماء مرضى، لا هوية وطنية، لا هاتف، لا ملاحظات |
| التحليلات | تجميع + منع الخلايا < 5 حالات |
| الشاشات العامة | آخر 4 أرقام من الهوية، بلا عنوان كامل |
| التصدير | يتطلب صلاحية + سبب + تدقيق |

> **قاعدة:** لا تُخفِ حقلاً في React فقط. الـ DTO يجب ألا يحتوي الحقل أصلاً لمن لا يملك الصلاحية.

---

## 10. نموذج التهديد (STRIDE مختصر)

| التهديد | المثال | الضابط |
|---------|--------|--------|
| Spoofing | انتحال جلسة | Cookie HttpOnly + SameSite + تدوير + إبطال |
| Tampering | تعديل فاتورة/تدقيق | قيود DB + Append-only + Hash chain |
| Repudiation | "لم أصرف الدواء" | Audit موقّع بالمستخدم والوقت |
| Information Disclosure | طبيب يرى كل المرضى | ABAC + DTO + تدقيق قراءة |
| Denial of Service | محاولات دخول | Rate limit + قفل + Connection pool |
| Elevation of Privilege | استقبال يعتمد نتيجة | فصل أدوار + `@RequiresPermission` + اختبار 403 |
| Injection | بحث مريض | JPA parameterized + sort allowlist |
| XSS | ملاحظة سريرية | React escaping + CSP + بلا `dangerouslySetInnerHTML` |
| CSRF | حذف موعد | SameSite + Token |
| Session Fixation | جلسة قديمة | تدوير عند الدخول |
| Broken Access Control | تغيير `patientId` في URL | تحقق ملكية/علاقة في الخادم |
| Sensitive Data Exposure | سجل يحتوي مريضاً | لا PII في Logs + Masking |

---

## 11. رؤوس الأمان

```text
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Permissions-Policy: geolocation=(), camera=(), microphone=()
Cache-Control: no-store (للبيانات الطبية)
```

- لا CORS في الإنتاج (نفس الأصل).
- في التطوير، Vite Proxy يلغي الحاجة إلى CORS.
- لا CDN خارجي في شاشات المرضى (CSP).

---

## 12. اختبارات الأمان الإلزامية

| # | الاختبار | المتوقع |
|---|----------|---------|
| S1 | استقبال يفتح سجل طبي | `403` + Audit |
| S2 | طبيب يفتح مريضاً غير مرتبط | `403` + Audit |
| S3 | طبيب يفتح مريضه | `200` + Audit قراءة |
| S4 | `LAB_TECHNICIAN` يعتمد نتيجته | `403` |
| S5 | `LAB_SUPERVISOR` يعتمد نتيجة فني آخر | `200` |
| S6 | محاسب يفتح تشخيصاً | `403` |
| S7 | مدير نظام يمنح نفسه `DOCTOR` | `403`/تدقيق |
| S8 | تغيير `patientId` في URL | `403` لا `200` |
| S9 | طلب POST بلا CSRF | `403` |
| S10 | Cookie بدون `HttpOnly` في الإنتاج | فشل فحص |
| S11 | 5 محاولات دخول خاطئة | قفل + Audit |
| S12 | Break-glass بسبب قصير | `422` |
| S13 | Break-glass صالح | وصول + إشعار + Audit |
| S14 | تعديل `audit_event` مباشرة | منع DB |
| S15 | حذف صف من السلسلة | فشل التحقق |
| S16 | كلمة مرور صحيحة بعد التجزئة | Argon2 verify |
| S17 | SQL Injection في البحث | لا تنفيذ + لا تسريب |
| S18 | XSS في ملاحظة | مُعامل كنص |
| S19 | جلسة منتهية | `401` |
| S20 | تسجيل خروج | إبطال فوري |

---

## 13. قائمة تحقق أمنية قبل التسليم

- [ ] كل endpoint له صلاحية أو `@PermitAll` مبرر.
- [ ] كل عملية حساسة تُدقَّق.
- [ ] لا كلمة مرور/سر في Git أو Logs.
- [ ] Argon2id مع Salt ومعاملات مخزنة.
- [ ] Cookies: HttpOnly + SameSite + Secure (إنتاج).
- [ ] CSRF على كل تغيير حالة.
- [ ] Rate limit على الدخول وBreak-glass.
- [ ] ABAC مطبق في الاستعلامات لا بعدها.
- [ ] DTO لا يحتوي حقولاً غير مصرح بها.
- [ ] Audit append-only + Hash chain + تحقق.
- [ ] رؤوس أمان + CSP + HSTS.
- [ ] نسخ احتياطي مشفر + استعادة مجرّبة.
- [ ] فحص تبعيات (`mvn dependency-check`, `npm audit`).
- [ ] اختبارات S1–S20 خضراء.

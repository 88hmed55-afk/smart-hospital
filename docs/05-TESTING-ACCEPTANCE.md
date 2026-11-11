# 05 — خطة الاختبار ومعايير القبول

> **القاعدة:** المشروع لا يُقيَّم بعدد الشاشات، بل بعدد السيناريوهات التي يفشل فيها بأمان.
> **الهدف:** إثبات أن النظام يحفظ البيانات، يمنع الازدواج، يرفض غير المصرح، ويتعافى من فشل الإشعار.

---

## 1. هرم الاختبار

```text
        ┌─────────────────────┐
        │  UAT / Demo (يدوي)  │  سيناريوهات الدور والعرض
        ├─────────────────────┤
        │  E2E / API (REST)   │  REST Assured على GF منشور
        ├─────────────────────┤
        │ Integration (DB)    │  JPA + PostgreSQL + قيود
        ├─────────────────────┤
        │ Unit (JUnit/Mockito)│  قواعد الحالة والحسابات
        └─────────────────────┘
```

| النوع | الأداة | ماذا يثبت؟ | أين يعمل؟ |
|-------|--------|------------|-----------|
| Unit | JUnit 5 + AssertJ + Mockito | State machines، حسابات، صلاحيات | بدون خادم |
| Integration | JUnit + PostgreSQL (Testcontainers أو DB اختبار) | JPA، قيود، معاملات | Maven |
| API/E2E | REST Assured + Failsafe | عقد API، أدوار، أخطاء | GF منشور |
| Concurrency | ExecutorService + CountDownLatch | منع الازدواج | GF + DB |
| Security | REST Assured + OWASP ZAP (اختياري) | 401/403/CSRF/XSS | GF |
| Performance | JMeter/Gatling | P95، أخطاء، Pool | GF + DB |
| Migration | Flyway + قاعدة فارغة/نسخة | مخطط قابل للتكرار | CI |
| Recovery | pg_dump/pg_restore | RTO/RPO | محلي |

---

## 2. بيئات الاختبار

| البيئة | قاعدة البيانات | الغرض |
|--------|----------------|-------|
| `test` | `smart_hospital_test` (تُعاد إنشاؤها) | اختبارات تكامل سريعة |
| `it` | Testcontainers PostgreSQL أو DB مخصص | اختبارات API |
| `demo` | `smart_hospital` + Seed | العرض |
| `prod-like` | نسخة من `demo` | أداء واستعادة |

- كل اختبار تكامل يبدأ من حالة معروفة (Flyway clean/migrate أو transaction rollback).
- لا تستخدم قاعدة `demo` في الاختبارات الآلية.
- لا بيانات حقيقية في أي بيئة.

---

## 3. مصفوفة القبول (من وصف المشروع + إضافات)

| # | السيناريو | النتيجة المتوقعة | مستوى الاختبار |
|---|-----------|------------------|----------------|
| A1 | حجز طبيب في موعد متعارض | `409` + لا صف جديد | API + Concurrency |
| A2 | موظف استقبال يعتمد نتيجة مختبر | `403` + Audit denied | API + Security |
| A3 | صرف كمية أكبر من المتاح | `409` + Rollback كامل | Integration |
| A4 | خطأ بعد صرف البند الأول | لا حركة مخزون ولا `dispense` | Integration |
| A5 | فشل إرسال إشعار المختبر | النتيجة محفوظة + إشعار لاحق | Integration + Outbox |
| A6 | تخصيص سرير محجوز | `409` + لا تخصيص مزدوج | API + Concurrency |
| A7 | مستخدم غير مصرح يفتح سجلاً طبياً | `403` + Audit | API + Security |
| A8 | تقرير كبير | `202` + Job + Polling + تنزيل | API |
| A9 | دفعة مكررة بنفس المفتاح | إيصال واحد فقط | Concurrency |
| A10 | تعديل سجل تدقيق | منع DB + فشل تحقق السلسلة | Integration |
| A11 | انتهاء الجلسة | `401` | API |
| A12 | طلب POST بلا CSRF | `403` | API |
| A13 | 5 محاولات دخول خاطئة | قفل + Audit | API |
| A14 | اعتماد نتيجة من نفس مدخلها | `403` (SoD) | API |
| A15 | Break-glass بسبب قصير | `422` | API |
| A16 | Break-glass صالح | وصول مؤقت + إشعار + Audit | API |
| A17 | فاتورة `paid_total` لا يطابق الدفعات | بطاقة CRITICAL + توفيق | Integration |
| A18 | مخزون سالب | قيد DB يمنع + DQ card | Integration |
| A19 | دفعة منتهية الصلاحية | لا تُخصص FEFO | Integration |
| A20 | ترحيل Flyway على قاعدة فارغة | نجاح + مخطط مطابق | CI |
| A21 | استعادة نسخة احتياطية | بيانات سليمة + زمن موثق | Manual |
| A22 | 100 مستخدم متزامن | P95/P99 ضمن الهدف | Performance |
| A23 | وصول طبيب لمريضه | `200` + Audit read | API |
| A24 | وصول طبيب لمريض غير مرتبط | `403` + Audit | API |
| A25 | إعادة إرسال صرف بنفس المفتاح | لا صرف ثانٍ | API + Concurrency |
| A26 | إلغاء دفعة | قيد عكسي، لا حذف | Integration |
| A27 | تحويل سرير | إغلاق القديم + فتح الجديد بذرّية | Integration |
| A28 | تقرير بلا بيانات | حالة واضحة، لا `500` | API |

---

## 4. سيناريوهات التزامن (Concurrency)

### 4.1 قالب الاختبار

```java
@Test
void twoConcurrentDispenses_neverGoNegative() throws Exception {
  Long rxId = fixtures.approvedPrescriptionWithStock(10);

  ExecutorService pool = Executors.newFixedThreadPool(2);
  CountDownLatch start = new CountDownLatch(1);

  Callable<Integer> call = () -> {
    start.await();
    return restClient.dispense(rxId, UUID.randomUUID().toString());
  };

  Future<Integer> f1 = pool.submit(call);
  Future<Integer> f2 = pool.submit(call);
  start.countDown();

  List<Integer> statuses = List.of(f1.get(), f2.get());
  pool.shutdown();

  assertThat(statuses).containsExactlyInAnyOrder(200, 409);
  assertThat(db.batchQty(drugId)).isEqualTo(0);
  assertThat(db.dispenseCount(rxId)).isEqualTo(1);
  assertThat(db.stockMovementCount(rxId)).isEqualTo(1);
}
```

### 4.2 الحالات المطلوبة

| السيناريو | التزامن | المتوقع |
|-----------|---------|---------|
| صرف آخر كمية | 2–10 خيوط | 1 نجاح، الباقي `409`، المخزون ≥ 0 |
| نفس `Idempotency-Key` | 5 خيوط | دفعة/صرف واحد |
| حجز نفس الموعد | 20 خيطاً | 1 نجاح، 19 `409` |
| تخصيص نفس السرير | 10 خيوط | 1 نجاح، 9 `409` |
| تحديث مريض بنسخة قديمة | 2 خيوط | 1 `200`، 1 `409 CONCURRENT_UPDATE` |
| اعتماد نتيجة أثناء إدخالها | 2 خيوط | لا اعتماد بلا نتيجة مكتملة |

### 4.3 أدوات مساعدة

- `CountDownLatch` لبدء متزامن حقيقي.
- `ExecutorService` بعدد الخيوط.
- تحقق من DB مباشرة بعد الاختبار (JDBC).
- لا تعتمد على `Thread.sleep`؛ استخدم Latch/Future.
- نظّف البيانات قبل/بعد كل اختبار.

---

## 5. اختبارات الفشل والتراجع (Failure & Rollback)

### 5.1 صرف بفشل طبيعي

أنشئ وصفة ببندين:
- الدواء A: كمية كافية.
- الدواء B: كمية غير كافية.

**المتوقع:**
- الاستجابة `409 INSUFFICIENT_STOCK`.
- `qty_on_hand` للدواء A لم يتغير.
- لا `dispense` ولا `dispense_item` ولا `stock_movement`.
- Audit يفشل؟ لا: العملية لم تُسجَّل كنجاح. إن سُجّل محاولة، فتكون `FAILURE` في معاملة منفصلة.

### 5.2 فشل JMS

| الخطوة | الفعل | التحقق |
|--------|-------|--------|
| 1 | أوقف الناشر (Config flag) أو أوقف Broker | — |
| 2 | اعتمد نتيجة مختبر | `200` + النتيجة في DB |
| 3 | افحص Outbox | صف `NEW` أو `FAILED` |
| 4 | شغّل الناشر | `PUBLISHED` |
| 5 | افحص Inbox الطبيب | إشعار واحد |
| 6 | أعد تشغيل الناشر | لا إشعار ثانٍ (Idempotent) |

### 5.3 فشل قاعدة البيانات

- أوقف PostgreSQL أثناء طلب → `503`/`500` منظم + `traceId`، لا تسريب.
- أعد التشغيل → التطبيق يتعافى (Connection pool).
- تحقق من عدم بقاء معاملات معلقة.

### 5.4 فشل الترحيل

- طبّق `V1..Vn` على قاعدة فارغة → نجاح.
- طبّق نفس السكربتات مرة أخرى → Flyway يرفض التكرار بأمان.
- عدّل سكربتاً مطبقاً → يجب أن يفشل التحقق (لا تعدل التاريخ).

---

## 6. اختبارات الأمان

استخدم المصفوفة S1–S20 في `03-SECURITY-RBAC.md`. الحد الأدنى:

| الفئة | الاختبارات |
|-------|-----------|
| Authentication | S11, S19, S20, Argon2 verify |
| Authorization | S1–S8, S12–S14 |
| Session/CSRF | S9, S10, S19 |
| Injection/XSS | S17, S18 |
| Audit | S13–S15 |
| Data exposure | فحص DTO لا يحتوي حقولاً سريرية للمحاسب/الاستقبال |
| Headers | HSTS/CSP/X-Frame في الإنتاج |

### أدوات

- **REST Assured** لطلبات 200/401/403.
- **OWASP ZAP Baseline** (اختياري) في CI أو محلي.
- **Dependency Check**: `mvn org.owasp:dependency-check-maven:check`.
- **npm audit**.

---

## 7. اختبارات الأداء

### 7.1 الأهداف

| المقياس | الهدف |
|---------|-------|
| قراءة قائمة (20 صفاً) P95 | < 500 ms |
| تفاصيل مريض P95 | < 700 ms |
| كتابة عملية (صرف/دفع) P95 | < 1,000 ms |
| نسبة الأخطاء | < 1% |
| استقرار الذاكرة | لا تسريب خلال 30 دقيقة |
| Connection pool | لا استنزاف |

### 7.2 الحمل

- 100 مستخدم متزامن، 10 دقائق.
- مزيج: 70% قراءة، 20% كتابة خفيفة، 10% عمليات حساسة.
- بيانات: 50,000 موعد، 20,000 زيارة، 100,000 حركة، 200,000 Audit.
- راقب: زمن الاستجابة، CPU، DB locks، slow queries، JMS depth.

### 7.3 سيناريوهات

| السيناريو | المقياس |
|-----------|---------|
| قائمة مرضى ببحث | P95 |
| Worklist مختبر | P95 |
| صرف دواء | P95 + أخطاء التعارض |
| دفعة مالية | P95 + Idempotency |
| تقرير 100k صف | لا يحجب API؛ Job + progress |
| تسجيل دخول | P95 + rate limit |

### 7.4 تحليل

- `pg_stat_statements` لأبطأ الاستعلامات.
- `EXPLAIN (ANALYZE, BUFFERS)` للاستعلامات الحرجة.
- أضف فهرساً فقط بعد قياس.
- وثّق قبل/بعد في تقرير الأداء.

---

## 8. اختبارات الترحيل والاستعادة

### 8.1 Flyway

```powershell
# من جذر المشروع (حيث pom.xml الأب) مع إعداد flyway-maven-plugin
mvn flyway:migrate `
  -Dflyway.url=jdbc:postgresql://localhost:5432/smart_hospital_test `
  -Dflyway.user=smart_hospital
```

- قاعدة فارغة → `V1..Vn` تنجح.
- إعادة التشغيل → لا تغييرات.
- `flyway:info` يعرض الحالة.
- اختبار على نسخة من `demo` قبل التسليم.

### 8.2 النسخ والاستعادة

```powershell
pg_dump -U smart_hospital -h localhost -Fc -f backup_before_release.dump smart_hospital
pg_restore -U smart_hospital -h localhost -d smart_hospital_test --clean --if-exists backup_before_release.dump
```

- سجّل **RTO** (زمن الاستعادة) و**RPO** (أقصى فقد مقبول).
- تحقق من عدد الصفوف في 5 جداول رئيسية بعد الاستعادة.
- جرّب الاستعادة قبل العرض بيوم.

---

## 9. اختبارات قبول المستخدم (UAT)

### 9.1 سيناريوهات الأدوار

| الدور | السيناريو | النجاح |
|-------|-----------|--------|
| استقبال | تسجيل مريض → حجز → تسجيل وصول → طابور | لا تكرار، ترتيب صحيح |
| طبيب | فتح زيارة → حيوية → تشخيص → طلب مختبر → وصفة | كل شيء محفوظ ومتسلسل |
| ممرضة | triage → علامات حيوية | تظهر للطبيب |
| مختبر | استلام → نتيجة → اعتماد | إشعار للطبيب |
| صيدلي | وصفة → صرف FEFO | خصم صحيح + فاتورة |
| محاسب | فاتورة → دفعة → إيصال | رصيد صحيح |
| مدير | KPI → بطاقة → خطة → قياس أثر | دورة كاملة |
| مدقق | بحث تدقيق → تحقق سلسلة | سليمة |
| مدير نظام | مستخدم/دور → دخول | صلاحيات صحيحة |

### 9.2 معايير القبول النهائية

- كل Must يعمل من الواجهة (ليس API فقط).
- كل سيناريو A1–A28 أخضر.
- لا أخطاء 500 في المسار الطبيعي.
- الرسائل العربية واضحة، والأخطاء قابلة للفهم.
- الأداء ضمن الهدف.
- النسخ/الاستعادة موثقة.
- لا بيانات حقيقية/أسرار في Git.

---

## 10. التتبع مع المتطلبات (Traceability)

| المتطلب | الاختبارات |
|---------|------------|
| FR-01 تسجيل الدخول والصلاحيات | S1–S20, A11–A13 |
| FR-02 المرضى | A23–A24, UAT |
| FR-03 الأطباء والأقسام | UAT, seed |
| FR-04 المواعيد ومنع التعارض | A1, concurrency |
| FR-05 الزيارات | UAT, API |
| FR-06 الحيوية والتشخيص | UAT, validation |
| FR-07 المختبر | A2, A5, A14, A19 |
| FR-08 الصيدلية | A3, A4, A9, A25 |
| FR-09 الفواتير والمدفوعات | A9, A17, A26 |
| FR-10 المخزون | A18, A19 |
| FR-11 التنويم والأسرّة | A6, A27 |
| FR-12 الإشعارات | A5, A8 |
| FR-13 التقارير والذكاء | A8, A22, UAT |
| FR-14 التدقيق | A10, A15, A16 |

---

## 11. CI والبوابات

### 11.1 خط الأنابيب

```yaml
# .github/workflows/ci.yml (مختصر)
jobs:
  backend:
    services:
      postgres:
        image: postgres:16
        env: { POSTGRES_DB: smart_hospital_test, POSTGRES_USER: smart_hospital, POSTGRES_PASSWORD: test }
    steps:
      - checkout
      - setup-java 17
      - mvn -B verify
      - mvn -B org.owasp:dependency-check-maven:check
  frontend:
    steps:
      - checkout
      - setup-node 22
      - npm ci
      - npm run lint
      - npm run typecheck
      - npm run test
      - npm run build
```

### 11.2 بوابات الدمج

- ✅ `mvn verify` ينجح (Unit + Integration).
- ✅ اختبارات API على GF أو Testcontainers.
- ✅ `tsc --noEmit` وESLint.
- ✅ Flyway على قاعدة فارغة.
- ✅ OpenAPI صالح.
- ✅ لا ثغرات High/Critical معروفة (مع استثناءات موثقة).
- ✅ تغطية الخدمات ≥ 60% (مؤشر لا هدف).
- ✅ لا `TODO` في مسار حرج.

> اختبارات E2E على GlassFish يمكن أن تكون بطيئة. شغّلها في nightly أو قبل الدمج، لا في كل حفظ.

---

## 12. قوالب

### 12.1 تقرير اختبار

```text
1. الملخص التنفيذي
2. البيئة (GF/PG/JDK/Commit)
3. النطاق (ما اختُبر وما لم يُختبر)
4. النتائج: A1..A28 + S1..S20
5. العيوب: ID/الوصف/الشدة/الحالة
6. الأداء: P50/P95/P99 + الأخطاء
7. الأمان: النتائج والاستثناءات
8. الترحيل/الاستعادة: RTO/RPO
9. المخاطر المتبقية
10. التوقيع
```

### 12.2 بلاغ عيب

```text
العنوان: [LAB] اعتماد النتيجة ينجح لمن أدخلها
الخطوات: ...
المتوقع: 403 + Audit
الفعلي: 200
البيئة: ...
الدليل: log/traceId/screenshot
الشدة: High
```

---

## 13. أخطاء شائعة في اختبار المشاريع

1. اختبار "النجاح" فقط → لا يثبت شيئاً.
2. اختبار التزامن بـ `Thread.sleep` بدلاً من Latch.
3. الاعتماد على ترتيب تنفيذ الاختبارات.
4. قاعدة اختبار مشتركة وبيانات متبقية.
5. اختبار الواجهة فقط وتجاهل API/DB.
6. تجاهل اختبار الفشل (JMS/DB/Constraint).
7. عدم قياس الأداء قبل التحسين.
8. عدم اختبار الاستعادة.
9. عدم ربط الاختبار بالمتطلب.
10. تجاهل 403/401 لأن "الواجهة تخفي الزر".

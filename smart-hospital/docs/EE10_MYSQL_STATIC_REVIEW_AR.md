# تقرير المراجعة المصدرية — Smart Hospital 1.0.0-ee10-mysql

**تاريخ المراجعة:** 8 نوفمبر 2026

**الهدف:** مواءمة المشروع مع بيئة التطوير الموجودة: Apache NetBeans 21، وJDK 17.0.12، وGlassFish 7.0.23، وMySQL 8.0.38، وNode.js 26.4.0.

## القرارات التقنية المعتمدة

- Jakarta EE 10 باستخدام `jakarta.jakartaee-api:10.0.0` بنطاق Maven `provided`.
- Java `release 17`.
- Jakarta Persistence 3.1، مع EclipseLink 4 المضمن في GlassFish 7.
- MySQL 8.0.38 / InnoDB بدلاً من PostgreSQL.
- Connector/J 9.7.0 كمشغل JDBC مقترح، مع تثبيته داخل نطاق Domain الخاص بـ GlassFish.
- مصدر JTA/XA باسم `jdbc/SmartHospitalDS` وقاعدة `hospital`.
- React + TypeScript + Vite، مع تثبيت إصدارات الحزم في `package.json` وعدم استخدام نطاقات `^` أو `~`.
- يلزم توزيع GlassFish Jakarta EE Platform 10 / Full Platform وليس Web Profile لأن المشروع يستخدم JMS وMessage-Driven Beans.

## فحوصات مصدرية أُجريت في بيئة الإعداد

| الفحص | النتيجة |
|---|---:|
| ملفات Java المحللة باستخدام مستوى اللغة `--release 17` | 211 |
| أخطاء صياغة Java | 0 |
| ملفات TypeScript/TSX التي اجتازت transpile syntax check | 24 |
| أخطاء الصياغة في TypeScript/TSX | 0 |
| ملفات XML التي تم تحليلها بنجاح | 5 |
| جداول MySQL في مخطط التثبيت المجمع | 64 |
| كيانات JPA مع `@Table` | 46 |
| أخطاء مطابقة جدول/أعمدة كيانات JPA مع MySQL schema | 0 |
| علاقات FK المكتشفة في CREATE TABLE وALTER TABLE | 155 |
| علاقات FK ذات عمود تابع/مرجعي أو مفتاح أب غير موجود | 0 |
| MySQL triggers | 75 |
| أسماء triggers المكررة | 0 |
| مسارات OpenAPI | 78 |
| مخططات OpenAPI | 89 |
| مراجع OpenAPI المحلية | 346 |
| مراجع OpenAPI المفقودة | 0 |
| بقايا صيغ PostgreSQL المعروفة داخل ملفات مسار MySQL التنفيذي | 0 |

## إصلاحات تمت أثناء المواءمة

1. تغيير Maven من Jakarta EE 11 إلى Jakarta EE 10 وتثبيت Java 17.
2. ضبط `persistence.xml` على Persistence 3.1 وMySQL وJTA DataSource.
3. استبدال مسار قاعدة البيانات التنفيذي بترحيلات MySQL خاصة، مع إبقاء ملفات PostgreSQL القديمة كسجل تاريخي غير قابل للتشغيل ضمن هذا الإصدار.
4. نقل القيود التي كانت تعتمد على PostgreSQL إلى generated columns وunique indexes أو قواعد تحقق/قفل معاملات في MySQL.
5. تحويل triggers إلى صياغة MySQL، وتصحيح صياغات SQL غير المتوافقة التي ظهرت في مراجعة الملفات.
6. إصلاح خطأ صياغي في `AppointmentService` كان سيعيق بناء Java.
7. إضافة فحص يمنع سكربت التثبيت من الكتابة فوق مخطط `hospital` يحتوي بالفعل جداول أو عروضاً.
8. إضافة إعداد GlassFish لمصدر XA/JNDI، ومستخدم MySQL محدود الصلاحيات، وتعليمات إنشاء مستخدمي التطوير في File Realm.
9. تحديث دليل NetBeans/Windows وOpenAPI وملفات README لتطابق Jakarta EE 10 وMySQL.

## ما لم يتم التحقق منه تشغيلياً

لم تكن بيئة الإعداد تحتوي على Maven أو MySQL Server/Client أو GlassFish، ولم تكن قادرة على تنزيل اعتماديات البناء. لذلك لم يتم تنفيذ أي مما يلي:

- `mvn clean package` أو إنتاج WAR عبر Maven.
- تشغيل سكربتات SQL أو triggers على MySQL 8.0.38 فعلياً.
- إنشاء/اختبار GlassFish XA pool أو نجاح `ping-connection-pool`.
- نشر WAR على GlassFish 7.0.23.
- اختبار بدء JMS أو استقبال MDB للرسائل.
- `npm install` أو `npm run build` بعد تنزيل الحزم.
- اختبار End-to-End من React إلى MySQL.

تم تحليل صياغة Java باستخدام مترجم JDK 21 الموجود في بيئة الإعداد مع الخيار `--release 17`؛ ثم نُفِّذ لاحقاً بناء كامل باستخدام JDK 17 وMaven على جهاز التطوير. كما تم فحص صياغة TypeScript/TSX، ثم نُفِّذ `npm run build` بنجاح.

تم لاحقاً **التحقق التشغيلي الكامل** بتاريخ 2026-11-09: بناء EAR، تطبيق سكربتات MySQL، إنشاء مستخدم التطبيق، إعداد مصدر البيانات و`ping-connection-pool`، نشر التطبيق على GlassFish 7.0.23، بدء JMS/MDB، تسجيل الدخول بجميع الحسابات، فحص وحدات REST، فحص الصفحات في المتصفح لكل دور، واختبار End-to-End من React إلى MySQL عبر `/api/v1`. راجع `README.md` الرئيسي و`scripts/smoke-test.ps1` لإعادة تنفيذ هذه الفحوص.

## تحذير الاستخدام

البيانات التجريبية اصطناعية. هذا مشروع أكاديمي غير معتمد سريرياً؛ لا تستخدمه لحفظ بيانات مرضى حقيقيين أو لاتخاذ قرارات تشخيص أو علاج.

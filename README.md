<div dir="rtl" align="center">

<img src="docs/images/icon.png" width="112" alt="أيقونة مصاريفي">

# مصاريفي · Masarifi

**تطبيق لإدارة مصاريفك: إدخال يدوي، ميزانيات، تحليل، فواتير ونسخ احتياطي، وبياناتك تظل في تلفونك. ومع البنوك العُمانية تتسجّل مصاريفك بروحها من رسائل البنك.**

[![أحدث إصدار](https://img.shields.io/github/v/release/al3nkboot/masarifi?label=%D8%A7%D9%84%D8%A5%D8%B5%D8%AF%D8%A7%D8%B1&color=F25C54)](https://github.com/al3nkboot/masarifi/releases)
[![نسخة تجريبية](https://img.shields.io/github/v/release/al3nkboot/masarifi?include_prereleases&label=beta&color=4A90E2)](https://github.com/al3nkboot/masarifi/releases)
![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3DBE7A)

### [⬇️ تحميل التطبيق (APK)](https://github.com/al3nkboot/masarifi/releases)

الإصدار المستقر **2.0.0**، والإصدار التجريبي **2.1.0** يضيف قسم «الفواتير».

<img src="docs/images/home.png" width="23%" alt="المعاملات"> <img src="docs/images/analysis.png" width="23%" alt="التحليل"> <img src="docs/images/bills.png" width="23%" alt="الفواتير"> <img src="docs/images/sms.png" width="23%" alt="عمليات رسائل البنك">

<sub>المعاملات · التحليل · الفواتير (2.1) · عمليات مسجّلة من رسائل البنك. كل البيانات في الصور وهمية.</sub>

[English](#english)

</div>

<div dir="rtl">

## المزايا

تطبيق أندرويد لإدارة المصاريف على طريقة Money Manager، بثيم داكن وواجهة عربية (والإنجليزية متوفرة). تسجّل عملياتك يدويًا أو تلقائيًا من رسائل البنك. عملة واحدة للتطبيق تختارها من الإعدادات: الريال العُماني (الافتراضي)، الدرهم الإماراتي، الريال السعودي، الدينار الكويتي، الدينار البحريني، الريال القطري، الدولار، اليورو، أو الروبية الهندية. لا يدعم حاليًا عدة عملات في نفس الوقت.

### 📩 تسجيل تلقائي من رسائل البنك (البنوك العُمانية حاليًا)
- يقرأ رسائل SMS من البنوك العُمانية فقط حاليًا ويحوّلها إلى عمليات، مع قواعد قابلة للتعديل.
- يربط كل بطاقة بحسابها (البنك + آخر 4 أرقام).
- يدمج الرسالتين المكررتين لنفس العملية، ويسجّل التحويل بين حساباتك عملية تحويل واحدة، والسحب من الصراف تحويلًا إلى النقد.
- أداة «إصلاح العمليات التلقائية» لمراجعة العمليات القديمة وتصحيحها مع إمكانية التراجع.

### 📊 الميزانية والتحليل
- ميزانية شهرية وميزانية لكل فئة، مع تنبيهات عند 80% و100%.
- إحصائيات ورسوم بيانية وملخص مالي ونصائح حول إنفاقك، وتنبيه عند ارتفاع مفاجئ في المصروف.

### 🧹 بيانات مرتبة
- كشف التكرار بين ما تسجّله يدويًا وما يصل من رسائل البنك، مع شاشة لمراجعة المكررات المحتملة.
- تحديد عدة عمليات وحذفها مرة وحدة، مع زر «تراجع».
- عمليات متكررة (راتب، اشتراكات…).
- استيراد بياناتك من تطبيق Money Manager ‏(xlsx/CSV).

### ☁️ نسخ احتياطي ومزامنة عبر Google Drive
- نسخ احتياطي يومي تلقائي إلى مجلد تختاره في Drive، ويحتفظ بآخر 10 نسخ.
- مزامنة جهازين عن طريق ملف واحد مشترك في Drive.

### 🧾 الفواتير (تجريبي في 2.1)
- فواتير متكررة (شهرية، سنوية، أو كل عدة أشهر) مع تذكير قبل موعدها.
- زر «تم الدفع» يسجّل المصروف مباشرة، ويعرض دمجه مع رسالة البنك إن وُجدت.
- تقويم للفواتير وسجل للدفعات، وودجت على الشاشة الرئيسية.

## البنوك المدعومة

| البنك | الحالة |
|---|---|
| بنك مسقط (وميثاق) | قواعد مخصصة: مشتريات البطاقة، التحويل عبر الجوال، الخصم والإيداع |
| ثواني – بطاقة موجب | قواعد مخصصة: مشتريات البطاقة |
| بنك ظفار (وميسرة) | قواعد مخصصة للرسائل العربية: الخصم والإيداع |
| البنك الوطني العماني، صحار الدولي، بنك عمان العربي، HSBC عُمان، البنك الأهلي، بنك نزوى، العز الإسلامي | يتعرّف على المرسل ويطبّق القواعد العامة. لم تُختبر على رسائل حقيقية بعد |

التسجيل التلقائي يدعم حاليًا البنوك العُمانية فقط. بنكك من دولة ثانية، أو مو في القائمة، أو رسائله ما تنقرأ صح؟ شوف قسم [ساهم](#ساهم). باقي مزايا التطبيق تشتغل بدون رسائل البنك.

## الخصوصية

- **لا يوجد خادم ولا إعلانات ولا تتبّع.** التطبيق لا يطلب صلاحية الإنترنت أصلًا (`INTERNET` غير موجودة في الـ manifest).
- بياناتك محفوظة في تلفونك فقط. النسخ الاحتياطي والمزامنة يكتبان ملفًا في **حسابك أنت** على Google Drive عن طريق منتقي الملفات في النظام، بدون تسجيل دخول داخل التطبيق.
- الصلاحيات المطلوبة: قراءة واستقبال الرسائل (للتسجيل التلقائي)، والإشعارات (للتنبيهات والتذكيرات).
- أرقام البطاقات التي تربطها في الإعدادات تُحفظ على الجهاز فقط.
- التفاصيل: [سياسة الخصوصية](docs/privacy-policy.md).

## التثبيت

1. حمّل ملف `masarifi-<الإصدار>-release.apk` من [صفحة الإصدارات](https://github.com/al3nkboot/masarifi/releases). المتطلب: أندرويد 8.0 أو أحدث.
2. افتح الملف واسمح بـ«تثبيت التطبيقات من مصادر غير معروفة» للمتصفح أو مدير الملفات.
3. **تحذير Play Protect:** لأن التطبيق يطلب صلاحية الرسائل ولم يُنشر في Google Play، قد يظهر تحذير أو يُحظر التثبيت. اضغط «مزيد من التفاصيل» ← «التثبيت على أي حال». قد تختلف الأسماء حسب الجهاز.
4. افتح التطبيق واسمح بصلاحية الرسائل والإشعارات. إذا رفض أندرويد منح صلاحية الرسائل («إعداد محظور»): الإعدادات ← التطبيقات ← مصاريفي ← ⋮ ← «السماح بالإعدادات المحظورة»، ثم فعّلها.
5. من الإعدادات اربط بطاقاتك بحساباتك، ويمكنك استيراد الرسائل القديمة.

## ساهم

أكبر مساعدة: **عيّنات من رسائل بنكك** حتى نضيف بنوك أكثر، من عُمان أو من أي دولة ثانية. افتح [Issue جديد](https://github.com/al3nkboot/masarifi/issues/new?template=bank-sms.md) والصق نص الرسالة كما هو، بعد **إخفاء** رقم البطاقة والحساب والرصيد والأسماء (مثلًا `XXXX1234` ← `XXXX0000`).

البلاغات والاقتراحات مرحّب فيها كذلك.

### إضافة بنك (للمطوّرين)

كل البنوك في جدول واحد: `banks` في [`DefaultSmsConfig.kt`](app/src/main/java/com/midroid/masarifi/sms/DefaultSmsConfig.kt). إضافة بنك = إضافة عنصر `BankProfile` واحد بدون كود جديد:
- `senderIds` و`bodyNames`: اسم المرسل والكلمات التي تعرّف البنك في نص الرسالة.
- `rules`: قواعد `SmsRule` (regex) بالرموز `{AMT}` `{CUR}` `{MERCHANT}` `{CARD}` `{DATE}` `{ANY}`، وكل قاعدة `DEBIT` أو `CREDIT`.
- `debitKeywords` و`creditKeywords` (اختياري): كلمات خصم/إيداع إضافية خاصة بالبنك.

اكتب القواعد من رسالة حقيقية مُخفاة فقط (من [قالب عيّنة رسالة بنك](.github/ISSUE_TEMPLATE/bank-sms.md))، وأضف لها اختبارًا في `BankTableTest.kt`.

## البناء من المصدر

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # الاختبارات، ومنها اختبارات لقطات الشاشة (Robolectric)
```
يتطلب Android SDK و JDK 17. التفاصيل التقنية بالإنجليزية في [`docs/README.en.md`](docs/README.en.md).

## الحقوق

من تطوير [@midroid5G](https://x.com/midroid5G). مبني بـ Kotlin و Jetpack Compose و Material 3 و Room و WorkManager.

© جميع الحقوق محفوظة. المشروع بدون رخصة مفتوحة حاليًا.

</div>

---

## English

**Masarifi** is an Android expense tracker in the style of Money Manager, with a dark theme. Arabic is the default language and English is available. Add transactions manually, or have them logged automatically from bank SMS. The app uses one currency, chosen in Settings: OMR (default), AED, SAR, KWD, BHD, QAR, USD, EUR or INR. Multiple currencies at once are not supported.

**Automatic SMS logging currently supports Omani banks only:** dedicated rules for Bank Muscat, Bank Dhofar and Thawani/Mojab card; other Omani banks use generic rules that haven't been tested on real messages yet. Every other feature works without bank SMS.

- Budgets with 80%/100% alerts, charts, a financial summary and spending tips.
- Duplicate detection between manual entries and SMS, multi-select delete with undo, recurring transactions, and import from Money Manager.
- Automatic daily backup to your own Google Drive (keeps the last 10 copies), plus two-device sync through one shared Drive file.
- **2.1 beta:** recurring bills with reminders, a "Paid" button that records the expense, a bills calendar and a home-screen widget.
- **Privacy:** there's no server and no ads. The app doesn't even request the `INTERNET` permission. Your data stays on your phone and in your own Drive. See the [privacy policy](docs/privacy-policy.md).

**[Download the APK from Releases](https://github.com/al3nkboot/masarifi/releases)** (Android 8.0+). Play Protect may warn you because the app reads SMS. Tap *More details → Install anyway*.
If your bank (from any country) isn't supported, [open an issue](https://github.com/al3nkboot/masarifi/issues/new?template=bank-sms.md) with a **masked** sample SMS.
To add a bank in code: add one `BankProfile` entry (sender IDs, `SmsRule` regexes using `{AMT}` `{CUR}` `{MERCHANT}` `{CARD}` `{DATE}`, optional debit/credit keywords) to `banks` in [`DefaultSmsConfig.kt`](app/src/main/java/com/midroid/masarifi/sms/DefaultSmsConfig.kt), based only on a real masked sample ([template](.github/ISSUE_TEMPLATE/bank-sms.md)), plus a test in `BankTableTest.kt`.
Technical docs: [docs/README.en.md](docs/README.en.md). © All rights reserved. No open-source license has been granted.

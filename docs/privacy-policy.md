# سياسة الخصوصية — مصاريفي

**آخر تحديث:** 22 أكتوبر 2026 · **التطبيق:** `com.midroid.masarifi` · **الإصدار:** 2.1.1

## باختصار
- كل بياناتك (المعاملات، الحسابات، الفئات، الميزانيات، الفواتير، الإعدادات) **محفوظة على جهازك فقط**.
- **التطبيق لا يطلب صلاحية الإنترنت** (`INTERNET`)، وما عندنا أي خادم. ما نجمع ولا نستلم أي بيانات.
- **لا إعلانات، لا أدوات تحليل، لا متتبّعات**، ولا حسابات تسجيل دخول.
- النسخ الاحتياطي التلقائي لنظام أندرويد (Google Cloud / adb) و«النقل من جهاز لجهاز» **معطّلان** لهذا التطبيق، فبياناتك ما تنرفع للسحابة إلا عبر النسخ الاحتياطي اللي تختاره أنت داخل التطبيق.

## الرسائل النصية (SMS)
- يقرأ التطبيق رسائل البنوك **على الجهاز فقط** لتسجيل المعاملات تلقائيًا: عند وصول رسالة جديدة، وعند فحص صندوق الوارد (آخر 90 يومًا) إذا طلبت ذلك.
- الرسالة تُحلَّل محليًا، ويُحفظ منها فقط ما يلزم للمعاملة (المبلغ، التاريخ، التاجر، آخر 4 أرقام من البطاقة، اسم المرسل) مع بصمة (hash) لمنع التكرار. **نص الرسالة الكامل لا يُحفظ** ولا يُرسل لأي جهة.
- رسائل رموز التحقق (OTP) تُتجاهل.

## البيانات اللي يتعامل معها التطبيق
| البيانات | وين تنحفظ | هل تطلع من جهازك؟ |
|---|---|---|
| المعاملات والحسابات والفئات والميزانيات والفواتير والإعدادات | قاعدة بيانات داخل التطبيق على جهازك | لا، إلا إذا عملت نسخة احتياطية أو مزامنة أو تصدير بنفسك |
| أرقام البطاقات (آخر 4 أرقام) اللي تربطها في الإعدادات | على جهازك | ضمن النسخة الاحتياطية/المزامنة فقط إذا فعّلتها |

## النسخ الاحتياطي والمزامنة والتصدير
- النسخ الاحتياطي (يدوي، أو تلقائي في مجلد تختاره) وملف المزامنة بين الأجهزة يُكتبان **فقط في الملف أو المجلد اللي تختاره أنت** عبر منتقي الملفات في أندرويد (مثلًا Google Drive أو ذاكرة الجهاز). التطبيق ما يسجّل دخول لأي حساب؛ النظام هو اللي يتعامل مع Google Drive. إذا اخترت خدمة سحابية، تنطبق عليها سياسة تلك الخدمة.
- ملفات النسخ الاحتياطي والمزامنة وتصدير CSV **غير مشفّرة**؛ احفظها في مكان آمن.
- مشاركة التقارير (PDF وغيرها) تتم فقط لما تضغط مشاركة وتختار التطبيق المستلم.
- استيراد ملفات Money Manager يتم محليًا من ملف تختاره.

## الأذونات
- **قراءة واستقبال الرسائل** (READ_SMS، RECEIVE_SMS): للتسجيل التلقائي من رسائل البنك.
- **الإشعارات** (POST_NOTIFICATIONS): للتنبيهات وتذكيرات الفواتير.
- أذونات تضيفها مكتبة WorkManager لتشغيل المهام الخلفية (النسخ التلقائي، المزامنة، الفحص اليومي): WAKE_LOCK، ACCESS_NETWORK_STATE، RECEIVE_BOOT_COMPLETED، FOREGROUND_SERVICE. ‏ACCESS_NETWORK_STATE لا يسمح بالاتصال بالإنترنت.
- ما نطلب الوصول لجهات الاتصال أو الموقع أو الصور أو الإنترنت.

## حذف البيانات
احذف البيانات من التطبيق، أو امسح بيانات التطبيق، أو ألغِ تثبيته — وبتنحذف كل البيانات من جهازك. ملفات النسخ الاحتياطي والمزامنة والتصدير اللي حفظتها في مكان آخر (مثل Google Drive) تحذفها بنفسك.

## الأطفال
التطبيق أداة مالية عامة وغير موجّه للأطفال، وما يجمع بيانات من أي أحد.

## التغييرات والتواصل
بننشر أي تحديث لهذه السياسة في نفس الصفحة مع تاريخ التحديث.
للتواصل: [GitHub Issues](https://github.com/al3nkboot/masarifi/issues)

---

# Privacy Policy — Masarifi («مصاريفي»)

**Last updated:** 22 October 2026 · **App:** `com.midroid.masarifi` · **Version:** 2.1.1

## Summary
- All your data (transactions, accounts, categories, budgets, bills, settings) is **stored only on your device**.
- **The app does not request the Internet permission** (`INTERNET`) and we have no server. We collect and receive nothing.
- **No ads, no analytics, no trackers**, and no sign-in accounts.
- Android's system backup (Google cloud / adb) and device-to-device transfer are **disabled** for this app, so your data only reaches the cloud through a backup you set up yourself in the app.

## SMS
- The app reads bank SMS **on the device only** to record transactions automatically: when a new SMS arrives, and when you ask it to scan the inbox (last 90 days).
- Messages are parsed locally; only what the transaction needs is kept (amount, date, merchant, card last 4 digits, sender name) plus a hash to avoid duplicates. **The full message text is not stored** and is never sent anywhere.
- One-time password (OTP) messages are ignored.

## Data the app handles
| Data | Where it is stored | Does it leave your device? |
|---|---|---|
| Transactions, accounts, categories, budgets, bills, settings | App database on your device | No — only if you back up, sync or export yourself |
| Card numbers (last 4 digits) you link in settings | On your device | Only inside your backup/sync file, if you enable it |

## Backup, sync and export
- Backups (manual, or automatic into a folder you pick) and the multi-device sync file are written **only to the file or folder you choose** with Android's file picker (e.g. Google Drive or device storage). The app never signs in to any account; Android handles Google Drive. If you choose a cloud service, that service's policy applies.
- Backup, sync and CSV export files are **not encrypted**; keep them somewhere safe.
- Reports (PDF etc.) are shared only when you tap share and pick the receiving app.
- Money Manager imports are processed locally from a file you pick.

## Permissions
- **Read and receive SMS** (READ_SMS, RECEIVE_SMS): automatic recording from bank SMS.
- **Notifications** (POST_NOTIFICATIONS): alerts and bill reminders.
- Permissions added by the WorkManager library for background jobs (auto backup, sync, daily check): WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE. ACCESS_NETWORK_STATE does not allow Internet access.
- No access to contacts, location, photos or the Internet.

## Deleting data
Delete data in the app, clear the app's data, or uninstall it — all data is removed from your device. Backup, sync and export files you saved elsewhere (e.g. Google Drive) must be deleted by you.

## Children
The app is a general finance utility, not directed at children, and collects no data from anyone.

## Changes and contact
Updates to this policy will be posted on this page with a new date.
Contact: [GitHub Issues](https://github.com/al3nkboot/masarifi/issues)

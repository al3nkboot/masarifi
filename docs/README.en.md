# مصاريفي — Masarifi

Native Android expense tracker (Kotlin, Jetpack Compose, Material 3, Room, WorkManager), modeled on "Money Manager".
Arabic RTL by default (English available), Omani Rial (ر.ع., 3 decimals) by default.

## Build
```bash
source /etc/profile.d/android.sh
./gradlew --no-daemon assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew --no-daemon testDebugUnitTest    # parser/insights/money unit tests + Robolectric launch & screenshot tests
```
Screenshots rendered by `ScreenshotTest` / `Screenshots12Test` / `Screenshots13Test` land in `app/build/screenshots*/` (synthetic data only).

## Structure
- `sms/` – rule-based SMS parser (pure Kotlin): `SmsParser`, `DefaultSmsConfig` (bank sender IDs + regex rules, editable),
  `MerchantCategorizer` (keyword → category), `SmsReceiver` (incoming SMS) and `SmsImporter` (inbox scan).
- `data/` – Room entities/DAOs, `Repository` (transactions, recurring, SMS import, learning merchant→category), CSV backup.
- `analysis/Insights.kt` – Arabic/English tips, spike detection, budget thresholds.
- `data/sync/` – device sync: `SyncModel`, `SyncMerge` (LWW + tombstones), `SyncCodec` (JSON), `SyncService`, `SyncStore`.
- `work/` – `SyncWorker` (auto sync), `BackupWorker`, `DailyCheckWorker` (daily + on-change: recurring, 80%/100% budget alerts, spike alert), notifications.
- `ui/` – Compose screens: Trans (Daily/Calendar/Monthly/Total/Note), Add/Edit, Stats (pie/trend/budget/tips), Accounts, More, Search, Categories, Repeating, SMS settings.
- `tools/gen_strings.py` – generates `values/strings.xml` (EN) and `values-ar/strings.xml` (AR).

## Import from Money Manager (v1.1.0)
More › Backup › "استيراد من Money Manager" opens the system picker (.xlsx, CSV/TSV, HTML-table .xls; legacy binary .xls
is rejected with a hint). `data/mm/XlsxReader.kt` is a dependency-free xlsx reader (central-directory zip + SAX);
`MoneyManagerParser` maps the export columns `Date | Account | Category | Subcategory | Note | <CUR> | Income/Expense |
Description | Amount | Currency | Account` (the 3-letter currency column is the main-currency amount). Transfer-Out/
Transfer-In pairs become ONE transfer; unpaired transfer rows use the Category column as the counter-account.
`MoneyManagerImporter` shows a preview, then inserts everything in one Room transaction. Each row gets a stable
hash (`mm:` + sha256 of date|amount|account|to-account|category|note|occurrence) stored in `Txn.smsHash` (unique index),
so re-importing the same file skips existing rows.
Tests: synthetic fixture in `src/test/resources/mm` (regenerate with `tools/gen_mm_fixture.py`). Local-only check
against a real export: `MM_XLSX=/path/export.xlsx MM_EXPECTED=/path/expected.json ./gradlew --no-daemon testDebugUnitTest`
(those tests are skipped when the env vars are absent; never commit real exports).

## Google Drive backup (v1.1.0, no OAuth client)
More › "النسخ الاحتياطي على Google Drive" uses the Storage Access Framework: "Backup now" (ACTION_CREATE_DOCUMENT,
pick Drive), an optional automatic folder (ACTION_OPEN_DOCUMENT_TREE + persistable permission; daily WorkManager job
and optional debounced on-change backup, keeps the newest 10 `masarifi-backup-YYYYMMDD-HHmmss.json`), and restore
(ACTION_OPEN_DOCUMENT → preview → replace all data in one transaction). Format: versioned JSON
(`"format":"masarifi-backup"`, `schemaVersion` 3 since 1.3.0 (includes uuid/updatedAt; older files still restore), all tables + settings), see `data/backup/BackupCodec.kt`.
If the provider doesn't allow folder access (Drive on many phones), the app explains and manual backup still works.

## Duplicate detection (v1.2.0)
- Incoming bank SMS is matched against existing non-SMS entries (manual or Money Manager imports) with the same type and the exact amount in the base currency, within ±24/48/72 h (More → نافذة كشف المكررات; default 48 h). Transfers are never auto-linked.
  - Exactly one match: the SMS is **linked** to that entry and no new row is created. The entry gets SMS and link badges, and a notification "تم ربط رسالة البنك بعملية موجودة" offers an undo (split) action. The SMS data is kept in `sms_links` so a split can rebuild it exactly.
  - Several matches, or a weak match (outside the window but within 7 days): the SMS row is created and flagged "مكرر محتمل" (`possibleDupOf`).
- Manual save: if an entry with the same amount exists, an inline warning offers حفظ على أي حال / فتح العملية الموجودة / إلغاء, plus "دمج" when the existing entry is an SMS row (it updates that entry with your account, category and note).
- Review screen "المكررات المحتملة" (More, or the banner on the Transactions screen) covers two cases: SMS vs manual pairs, and generic exact duplicates (same amount, category, account and note within 10 min). Actions: دمج (keep the manual entry's fields plus the SMS metadata), إبقاء الكل (stored in `dup_dismissed`, never shown again), or delete one.

## Multi-select delete (v1.2.0)
Long-press a transaction (Transactions daily/calendar, search results, category/account drill-down) to enter selection mode, then tap to toggle or tap a day header to select that day. The top bar has a count, تحديد الكل and a delete icon. Deleting asks for confirmation and runs in one DB transaction. The "تراجع" snackbar re-inserts the exact rows (same ids, SMS hashes and import fingerprints). Back exits selection mode.

## Device sync between two phones (v1.3.0, Google Drive file, no OAuth/Firebase)
More › «مزامنة الأجهزة» merges the data of two (or more) phones through ONE shared JSON file, `masarifi-sync.json`,
accessed only via the Storage Access Framework (persistable read/write URI permission). This is separate from backups:
backups are restore points, while sync merges both devices.

**Setup (also explained on the screen)**
1. Phone A: «إنشاء ملف مزامنة» (ACTION_CREATE_DOCUMENT), then save `masarifi-sync.json` in Google Drive. The first sync writes A's data.
2. In the Google Drive app, share that file (or its folder) as **Editor** with phone B's Google account, or use the same account on both phones.
3. Phone B: «فتح ملف مزامنة موجود» (ACTION_OPEN_DOCUMENT) and pick **the same file**. A preview asks to confirm, then it merges (nothing is replaced).
4. «مزامنة الحين» on either phone, or enable auto-sync.

Both phones must use the *same* file. A re-uploaded copy or a same-name file elsewhere is a different file. Every file
has a `fileId`, shown as «معرّف الملف» on both phones so you can compare them. If the selected file's id differs from the one
this phone synced with, the app asks before merging.

**Triggers**: the «مزامنة الحين» button. When «مزامنة تلقائية» is on (default **off**), the app also syncs:
- with a WorkManager periodic job every 15/30/60/180/360 min (network required);
- on app resume (at most once a minute);
- 30 s after the last local change (debounced; skipped if nothing changed since the last sync).

**Sync cycle** (`data/sync/SyncService.kt`): read the file, then merge inside one DB transaction (export local, `SyncMerge.merge`, apply), then write to the same URI only when the file needs changes. A file that is not a sync file is never overwritten. The screen shows the last sync, this device's name (editable), the other devices seen in the file, and a log of the last 30 runs (pulled / pushed / merged / conflicts / errors).

**Data model**: every synced entity (accounts, categories, transactions, recurring rules, budgets, merchant→category
rules, SMS link metadata, and a subset of settings: currency, week start, dup window, alerts) has a stable `uuid` and
`updatedAt`. Deletes are recorded as tombstones (`sync_tombstones`, `deletedAt`). SQLite triggers (`data/SyncSchema.kt`)
keep this consistent on every code path:
- an update bumps `updatedAt`;
- a delete writes a tombstone;
- re-inserting a deleted uuid (undo/restore) drops the tombstone.

Built-in categories (`cat-<key>`), the default cash account (`acc-cash`), auto-created SMS/import accounts and categories
(name-based UUIDs), and recurring occurrences (`rec-<rule>-<date>`) have deterministic uuids. Because of this, the two phones merge these records instead of duplicating them.

**Merge rules** (`data/sync/SyncMerge.kt`, pure Kotlin):
- Union by uuid, last-write-wins per entity on `deletedAt ?: updatedAt`, compared at second resolution.
- A tombstone propagates and wins over older edits. A deleted entity is only resurrected by a strictly newer edit.
- Same-second tie: a delete wins over an edit. Otherwise the **local** version wins and is re-stamped one second later, so both phones converge on it. Identical content is never a conflict.
- Bank-SMS hashes and Money Manager import fingerprints stay unique. If both phones created the same SMS/import row, one is kept (lowest uuid) and the other becomes a tombstone.
- Integrity: a transaction or recurring rule whose account was deleted is deleted too, and so is an SMS link whose transaction is gone. Deleted categories leave transactions uncategorised. The SMS log and the "keep both" duplicate decisions are grow-only sets.
- Stats: pulled/pushed = entities changed locally/in the file. Merged = live entities after the merge. Conflicts = entities changed on both sides since the last sync, plus dropped duplicates.

**File format** (`data/sync/SyncCodec.kt`): `{"format":"masarifi-sync","version":1,"fileId","updatedAt","devices":{…},
"entities":{"txn":[{"uuid","updatedAt","deletedAt"?,…}],…},"smsLog":[…],"dismissed":[…]}`, with stable ordering. Unknown entity kinds are preserved, and files from a newer version are refused.

**Limitations**
- Not realtime. Changes arrive when the other phone syncs. The Drive document provider may serve a cached copy, and it uploads in the background, so expect a delay of a minute or more.
- If two phones write the file at the same moment, the last upload wins at the file level. Nothing is lost locally, and the next sync merges again.
- Drive's system picker often hides "Shared with me". Workaround: Drive app › Shared › ⋮ › "Add shortcut to Drive", or sign into A's account on B.
- Some providers do not grant persistable permissions. The app then warns, and you may need to re-pick the file.
- Tombstones are kept forever, which is small.
- Restoring a backup counts as a new edit: the restored rows become the newest version on the next sync. Backups from before 1.3 get new uuids, so restore them on one phone only, before setting up sync.
- Undo after delete re-inserts the same rows with a newer `updatedAt`.

## Repeat field fix (v1.3.0)
Opening an existing transaction always shows «التكرار» with its current value: none, daily, weekly, monthly or yearly. This includes SMS-created and imported rows. The value is read from the linked recurring rule (`Repository.repeatOf`). On save, `Repository.applyRepeatOnEdit` runs in one transaction:
- none → X creates a rule (next occurrence after this entry's date);
- X → Y updates the rule's frequency and template (amount/account/category/note) and reschedules from the latest occurrence;
- X → none deletes the rule and unlinks its transactions, which are kept.

If the repeat value has not been loaded yet, saving leaves the rule unchanged. Tests:
- `RepeatFieldEditTest` (Compose/Robolectric): save monthly → reopen → «كل شهر» shown → change to none → save → no rule left.
- `RepeatEditRepositoryTest`.

## Provider formats: Bank Muscat, Thawani Pay, Bank Dhofar (v1.4.0)
The rules are tuned to the real (bank-masked) messages. These are kept verbatim in `ProviderSamplesTest` (parser) and `ProviderImportTest` (end-to-end).
Sender IDs are matched ignoring case, spaces and hyphens: `bank muscat`/BankMuscat/BANKMUSCAT/Bank-Muscat, `Thawani Pay`/ThawaniPay/Thawani, `BankDhofar`/BKDHOFAR.

| Provider | Message | Recorded as |
|---|---|---|
| Thawani Pay | `Your Mojab Card 4000XX…5501 used successfully for .250 OMR at MERCHANT , OMN on dd/MM/yyyy . Your available balance is …` | Expense on account «ثواني – بطاقة موجب ••5501» (card). Amounts like `.225` are read as 0.250. The merchant is trimmed and ", OMN" is removed. PHARMACY/Medical → Health, BAKERY → Food |
| Bank Muscat | `Card of a/c …0099 used for OMR 50.000 at ThawaniPay Topup … on dd/MM/yyyy HH:mm:ss. Avl Bal …` | **Transfer**: «بنك مسقط ••0099» → the Thawani wallet account (created if missing; adopted by the first Mojab card SMS) |
| Bank Muscat | `You have sent OMR 30.000 to NAME from your a/c … on … using Mobile Payment services. Txn Id … Avl Bal …` | Expense, category «تحويلات لأشخاص» (created on first use, uuid `cat-p2p`). The Txn Id becomes the row's unique SMS key (`ref:bankmuscat:<id>`), so a re-sent message is not recorded twice. Recategorising teaches the recipient |
| Bank Muscat | `[#] OTP …` | ignored |
| Bank Dhofar | `حسابكم رقم 0200XXX0007001 قد أدخل فيه مبلغ OMR 2.000 بتاريخ yyyy-MM-dd. رصيدكم الحالي هو OMR …` | Income «أخرى» (or salary if it matches) on «بنك ظفار ••7001». Debit wordings `خصم/سحب منه مبلغ` → expense |
| Bank Dhofar | `تم إيقاف بطاقتك …` | ignored (card status) |

- **Balance check**: each balance printed in an SMS is stored per account in the local table `sms_balances`. If the previous balance is known and *new = previous − amount*, the transaction is recorded as a debit; if *new = previous + amount*, it is recorded as a credit. Otherwise the parsed direction is kept. See `sms/BalanceCheck.kt`.
- **Own-wallet top-ups**: SMS settings › «شحن محافظك» is a list of merchant patterns (case/space/punctuation-insensitive), each mapped to a target account. The default is ThawaniPay/Thawani Topup → the Thawani wallet account. A match becomes a transfer instead of an expense.
- **«إعادة فحص الرسائل»**: re-reads the last 90 days, but only messages from known bank/wallet senders and your extra senders. It uses the normal de-duplication: the same SMS hash, the same bank Txn Id, and linking to a matching manual entry.

## Database migrations
Room schemas are exported to `app/schemas/`. v1 (1.1.0) → v2 (1.2.0) is a non-destructive `Migration(1,2)` (`data/Migrations.kt`) that adds columns and tables. `MigrationTest` builds a v1 database from `1.json`, migrates it, and checks that the data is preserved.
v2 → v3 (1.3.0) is additive too (`MIGRATION_2_3`). It:
- adds `uuid` and `updatedAt` to categories, accounts, recurring, transactions and sms_links, and `updatedAt` to budgets;
- assigns uuids: `cat-<key>` for built-in categories, `acc-cash` for the default cash account, and random v4 for the rest;
- adds unique uuid indices;
- sets `updatedAt = createdAt` where available;
- creates `sync_tombstones` and the sync triggers.

v3 → v4 (1.4.0) only creates the local `sms_balances` table (`MIGRATION_3_4`). It is not synced or backed up, and it is cleared on restore.
`MigrationTest` migrates v1, v2 and v3 databases (built from the exported JSON) to v4: data is preserved, uuids are unique, triggers are active and `sms_balances` is usable. It also checks the exported `3.json` and `4.json`.

## Adding a bank / pattern
Edit `DefaultSmsConfig.kt`: add a `BankProfile` with `senderIds`, and optionally `SmsRule`s using tokens
`{CUR} {AMT} {CARD} {MERCHANT} {DATE} {ANY}`. Users can also add extra sender IDs in More › Smart SMS import.

## Install (sideload)
`adb install -r masarifi-debug.apk` or copy the APK to the phone and open it (allow "Install unknown apps").
SMS permissions are restricted on Google Play, so the SMS feature is intended for sideloading. On Android 13+ if the
SMS permission is blocked, open App info › ⋮ › Allow restricted settings.

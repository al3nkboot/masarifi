# Google Play — Data safety form (draft answers) · Masarifi 2.1.1

> Draft for the owner to review in Play Console → App content → Data safety. Based on the 2.1.1 code and merged
> manifest: no `INTERNET` permission, no networking code, no analytics/ads/crash SDKs (Room, WorkManager, Compose only).

## Data collection and security
| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Nothing can leave the device through the app itself (no `INTERNET` permission). The developer has no server and receives nothing. |
| Is all user data encrypted in transit? | *(not asked when "No" above)* | No network traffic. |
| Do you provide a way for users to request that their data be deleted? | *(not asked when "No")* — if asked: data is on-device; users delete it in-app / clear app data / uninstall | |

## Per data type (all "Not collected, not shared" by the developer)
| Category | Collected | Shared | Notes |
|---|---|---|---|
| Messages → SMS | No | No | Bank SMS are read and parsed on device; only extracted fields + a hash are stored; full text is not stored. |
| Financial info (transactions, budgets, bills, card last 4) | No | No | Stored on device only |
| Location, Personal info, Contacts, Photos/videos, Audio, Calendar, Health | No | No | |
| Files & docs | No | No | Backup/sync/CSV files are written only to a location the **user** picks via SAF (e.g. Google Drive); user-initiated, not collection |
| App activity, Web browsing, App info & performance | No | No | No analytics or crash reporting |
| Device or other IDs | No | No | No AD_ID permission |

## Other declarations
- **Ads:** No ads.
- **Android system backup:** disabled (`allowBackup="false"`, `fullBackupContent="false"`, `dataExtractionRules` exclude everything for cloud backup and device transfer).
- **SMS permissions:** READ_SMS / RECEIVE_SMS require Google Play's *Permissions Declaration* (SMS/Call Log policy); check eligibility before publishing on Play.
- **Target audience:** 18+ / general adults (finance utility).
- **Privacy policy URL:** `docs/privacy-policy.md` on GitHub (or host it via GitHub Pages).
- **Independent security review:** No.

## Consistency check
Matches `docs/privacy-policy.md` (on-device only, no Internet permission, SMS parsed locally, backups/sync only where the
user chooses and unencrypted, no ads/analytics/trackers).

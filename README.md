# Quota Vault (Android)

A native Android app (Kotlin + Jetpack Compose) for tracking your sales productivity.
Everything is stored privately on the phone — no account or internet needed.

## Features

| Tab | What it does |
|---|---|
| **Stats** | Close rate, upsell rate, upsell acceptance, **commission earned**, revenue, upsell revenue, average sale, average time per sale. Filter by Today / This week / This month / All time. Full sales list — tap to edit, trash icon to delete. Tap the commission card to set your default rate. |
| **Goals** | **Personal** and **Company** money goals for this week / month / quarter / year, with progress bars and pace ("On pace" or "$X/day needed"). Tracks revenue, commission or upsell revenue automatically from logged sales, or a number you update yourself. |
| **Clients** | Save first name, last name, **job / work order #**, phone, email and notes (search by any of them). Search, tap the phone or email icon to call/email, see each client's sales and close rate. |
| **Calendar** | Month view with dots on days that have appointments. **Highlight important days** in red, orange, yellow, green, blue, purple or pink. Add appointments with title, date, time, client, notes and a **reminder** (at start time up to 1 day before) that rings like an alarm, with Snooze. Shows that day's sales too. |
| **Timer** | Stopwatch for timing a sale. Pick the client, Start/Pause/Reset, then **Finish & log this sale** to save it with the time filled in. Keeps running if you switch tabs or leave the app. |
| **Charts** | Commission earned this week / month / quarter / year (with month-end pace and best month), per-sale averages (time, sale amount, commission, close rate, biggest sale) for any period, and 12-week or 12-month trend charts of commission, revenue, close rate and time per sale, plus closed sales by day of week. Tap a bar or point for its value; TalkBack reads every value. |
| **Calc** | Calculator with + − × ÷ and % (e.g. `1200 × 15%` = 180), live result preview. |

**Settings** (gear icon, top right): Security & privacy, Trade, Appearance, Accessibility, Text, Language.

**Accessibility:** high-contrast palette, bold text, larger touch targets (56dp), text up to 200%, color-blind-friendly calendar highlights (each color has its own symbol), and a full TalkBack pass (headings, spoken calendar days and calculator keys). Also works with Select to Speak, Switch Access and magnification.

**Sales trades:** General, Real estate, Insurance, Automotive, Home services & solar, Retail, B2B & software. Changes the app's wording (Clients / Customers / Homeowners / Accounts, Sale / Deal / Policy / Job, Upsell / Rider / Upgrade / Expansion, Appointment / Showing / Meeting / Site visit) and suggests a starting commission.

**Pipeline & follow-ups:** each client has a stage (Lead → Contacted → Proposal → Negotiating → Won / Lost) with filter chips, plus a follow-up date that becomes a calendar appointment with a reminder. "Follow-ups due" shows at the top of Clients.

**Welcome tour & demo mode:** first launch walks through the app, asks for your trade, and offers made-up sample data that can be removed in one tap (only the sample records are removed). Existing users skip the tour.

**Manager report (PDF):** Stats → Report (PDF). Built on the phone; client names and commission are left out unless turned on; optional AES-256 password.

**Home-screen widget:** goal progress as percentages only (never amounts or names). Shows "Locked" when app lock is on. The eye icon next to it is **privacy mode** (hides every dollar amount).

## Privacy & security

- **No internet permission.** The manifest strips it, and the CI privacy audit fails the build if it ever appears. Nothing can be sent anywhere. Every release lists its permissions.
- **Minimal permissions:** notifications, exact alarms (SCHEDULE_EXACT_ALARM, which the user allows once; USE_EXACT_ALARM is blocked by the CI audit because Play restricts it), run at startup (re-arm reminders), biometric. Nothing else.
- **Release build** (not debuggable) signed with the permanent key.
- **Encrypted at rest:** the data file is AES-256-GCM with a non-exportable Android Keystore key; older plain files are migrated and overwritten.
- **No Google cloud backup or device transfer** of app data (`allowBackup=false`, data extraction rules exclude everything). Users move data only with their own encrypted backup file.
- **Private lock-screen reminders**, privacy mode, re-lock timing, screenshot blocking, erase-all (destroys the key), delete a client with all their records, backup reminders, password strength meter.
- **User agreement** must be accepted on first launch (versioned in `SecurityOptions.kt` → `AGREEMENT_VERSION`). *Have an attorney review the English text before distribution.*

**Text:** six font styles (Standard, Easy reading — Atkinson Hyperlegible, Modern — Lexend, Rounded — Nunito, Classic serif, Compact) and text size 85–150%. Bundled fonts are under the SIL Open Font License; license files are in `app/src/main/assets/licenses/`.


**Security:** turn on **Lock with fingerprint or PIN** to require your fingerprint, face or phone PIN when opening the app (it re-locks after 30 seconds away and hides the app preview in recent apps).

**Encrypted backup:** **Back up to an encrypted file** saves all data in a password-protected file (AES-256-GCM, key derived from your password with PBKDF2-SHA256). Store it anywhere — Google Drive, Downloads, email. **Restore from a backup** brings it back on any phone. The password is never stored; if it's forgotten, the backup can't be opened.

**Languages:** 16 languages: English, Español, Français, Deutsch, Português, Italiano, Русский, Türkçe, 中文, 日本語, 한국어, Tiếng Việt, Bahasa Indonesia, हिन्दी, বাংলা and العربية (or follow the phone's language). Translations are machine-assisted; have native speakers review them before a wide launch. Money always stays in your phone's own currency.

**Colors:** under Appearance choose Classic Teal, Grayscale, Soft Sunset (orange), Soft Rose (red), Calm Sage (green), or **Custom colors** picked on a color wheel (main, accent and background; shades are adjusted automatically to keep text readable), plus Auto / Light / Dark mode.

### How the percentages are calculated
- **Close rate** = closed sales ÷ all sales logged (closed + not closed)
- **Upsell rate** = closed sales that included an upsell ÷ closed sales
- **Upsell acceptance** = upsells accepted ÷ upsells offered
- **Revenue** = sale amount + upsell amount, for closed sales only
- **Commission** = revenue × the commission rate saved on each sale (new sales start with your default rate)
- **Goal pace** = compares how much of the goal is done with how much of the time frame has passed

## Build and install

1. Install **Android Studio** (free): https://developer.android.com/studio
2. Unzip this folder, then in Android Studio choose **File → Open** and pick the `SalesTracker` folder.
3. Wait for Gradle sync to finish (first time downloads a few hundred MB). If Studio offers to upgrade the Android Gradle Plugin, it's safe to accept.
4. On your phone: **Settings → About phone → tap "Build number" 7 times** to enable Developer options, then turn on **USB debugging** in Developer options.
5. Plug the phone in, select it in the device dropdown at the top, and press **Run ▶**.

To get an installable APK file instead: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The file lands in `app/build/outputs/apk/debug/app-debug.apk` — copy it to the phone and open it (allow "install unknown apps" when asked).

Requires Android 8.0 or newer.

## Download from GitHub (no Android Studio needed)

Every push to `main` builds the app automatically (see `.github/workflows/build-apk.yml`).

1. On your phone, open the repository on github.com and tap **Releases** (right side, or scroll down on mobile).
2. Open the newest release and tap **QuotaVault.apk** to download it.
3. Open the downloaded file. Android will ask you to allow installs from your browser — allow it, then tap **Install**.

To update, install the newer APK over the old one — your saved data stays. This works because every build is signed
with the same permanent key, stored in the repository secret `SIGNING_KEYSTORE` (Settings → Secrets and variables → Actions).
Keep a backup of that key: if it's lost, the next build can only be installed after uninstalling, which erases the app's data.
If a build fails, the **Actions** tab on GitHub shows the error.

## Project layout

```
app/src/main/java/com/salestracker/app/
├── MainActivity.kt          bottom navigation + tabs
├── data/
│   ├── Models.kt            Client, Sale, Appointment
│   ├── Repository.kt        saves everything to a JSON file on the device
│   ├── Stats.kt             close / upsell rate math, time periods
│   ├── Calculator.kt        calculator expression engine
│   └── Format.kt            money, %, time formatting
└── ui/
    ├── AppViewModel.kt      app state, stopwatch logic
    ├── theme/Theme.kt       colors (light + dark mode)
    └── screens/             one file per tab, plus shared dialogs
```

Data file location on the phone: the app's private storage (`files/sales_data.json`). Uninstalling the app deletes it; Android's automatic backup includes it if backup is on.

## Google Play

- Every build attaches **QuotaVault.aab** (signed with the permanent key, which is the Play *upload key*) next to the APK. Upload the .aab in Play Console.
- Targets Android 16 (API 36), as Play requires for new apps from Aug 31, 2026.
- **Privacy policy:** `docs/privacy.html`, served by GitHub Pages at https://ray-aurelius.github.io/SalesTracker/privacy.html once Pages is enabled (Settings → Pages → Deploy from a branch → `main` / `/docs`).
- **Store screenshots:** the "Play Store screenshots" workflow renders the real screens with sample data (Robolectric) and attaches them to the `store-assets` release.

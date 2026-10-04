# Sales Tracker (Android)

A native Android app (Kotlin + Jetpack Compose) for tracking your sales productivity.
Everything is stored privately on the phone — no account or internet needed.

## Features

| Tab | What it does |
|---|---|
| **Stats** | Close rate, upsell rate, upsell acceptance, **commission earned**, revenue, upsell revenue, average sale, average time per sale. Filter by Today / This week / This month / All time. Full sales list — tap to edit, trash icon to delete. Tap the commission card to set your default rate. |
| **Goals** | **Personal** and **Company** money goals for this week / month / quarter / year, with progress bars and pace ("On pace" or "$X/day needed"). Tracks revenue, commission or upsell revenue automatically from logged sales, or a number you update yourself. |
| **Clients** | Save first name, last name, phone, email and notes. Search, tap the phone or email icon to call/email, see each client's sales and close rate. |
| **Calendar** | Month view with dots on days that have appointments. Add appointments with title, date, time, client, notes and a **reminder** (at start time up to 1 day before) that rings like an alarm, with Snooze. Shows that day's sales too. |
| **Timer** | Stopwatch for timing a sale. Pick the client, Start/Pause/Reset, then **Finish & log this sale** to save it with the time filled in. Keeps running if you switch tabs or leave the app. |
| **Calc** | Calculator with + − × ÷ and % (e.g. `1200 × 15%` = 180), live result preview. |

**Colors:** tap the palette icon at the top right to choose Classic Teal, Grayscale, Soft Sunset (orange), Soft Rose (red) or Calm Sage (green), and Auto / Light / Dark mode.

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
2. Open the newest release and tap **SalesTracker.apk** to download it.
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

# RunStats (Android / Java)

A personal native Android running stats, multi-year graphs, and optional AI running coach app built in **Java** (`MainActivity.java`) and compiled automatically via **GitHub Actions**.

Syncs your Garmin runs via your **free Strava account** into an offline local SQLite database on your phone—no backend server required and no API keys committed to GitHub.

## Features
- **Dashboard:** Yearly, 30-day, and all-time totals (distance, run count, avg pace, avg HR, elevation), weekly streaks, Acute:Chronic Workload Ratio (ACWR), aerobic efficiency trends, and period highlights.
- **Multi-Year Graphs (MPAndroidChart):**
  - Yearly distance bar chart
  - Cumulative year-over-year mileage comparison curves
  - Last 12 months distance bar chart
  - Monthly Avg Pace vs. Avg Heart Rate trend
- **Run History:** Searchable & year-filterable list of every synced run with full run details on tap.
- **Optional AI Running Coach:** Chat with Gemini grounded directly in your real multi-year & recent running metrics.
- **Units Toggle:** Switch cleanly between **Kilometers (`km` / `m`)** and **Miles (`mi` / `ft`)** in Settings.
- **Sample Data Mode:** Load 3 years of realistic sample runs in one tap to explore every graph before linking Strava.

---

## 1. Build the APK with GitHub Actions
1. Create a new repository on GitHub (private or public).
2. Push this project to `main`:
   ```bash
   git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
   git push -u origin main
   ```
3. Open the **Actions** tab on GitHub → click the latest **Build Android APK** run → download **`RunStats-debug-apk`** at the bottom and install `app-debug.apk` on your Android phone.

---

## 2. Connect Your Free Strava Account (2 minutes)
1. Make sure your Garmin Connect account auto-syncs to Strava (Garmin Connect app → Settings → Connected Apps → Strava).
2. Go to [strava.com/settings/api](https://www.strava.com/settings/api) and create a personal API application:
   - **Application Name:** `RunStats` (or anything you like)
   - **Category:** `Data Importer` or `Training`
   - **Website:** `http://localhost`
   - **Authorization Callback Domain:** `localhost` *(no `http://`, just `localhost`)*
3. Copy your **Client ID** and **Client Secret**.
4. Open **RunStats** on your phone → **Settings** → paste your Client ID & Client Secret → tap **Save & Connect with Strava** → authorize!
   - *Tip:* If your mobile browser shows a page instead of auto-returning to the app, copy the URL from the browser's address bar and tap **Paste callback URL / code manually** in Settings.
5. After connecting, the app automatically syncs your full running history into local storage. Future syncs only fetch new runs!

---

## 3. Optional: Enable AI Running Coach
1. Get a personal Gemini API key at [aistudio.google.com](https://aistudio.google.com/).
2. Open **Settings** in RunStats → paste your **Gemini API Key** → tap **Save AI Settings**.
3. Open the **AI Coach** tab and tap a quick prompt or ask your own question!

*(Optional: If you use a private repo and want your keys pre-filled in builds, you can also add `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, and `GEMINI_API_KEY` as GitHub Repository Secrets — they will never be committed to Git.)*

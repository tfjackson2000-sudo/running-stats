# RunStats (Android / Java)

A personal native Android running stats, multi-year graphs, and optional AI running coach app built in **Java** (`MainActivity.java`) and compiled automatically via **GitHub Actions**.

Syncs your **Garmin Connect** runs **100% free** via **Intervals.icu** (no Strava subscription required) OR imports Garmin Connect `Activities.csv` files directly offline.

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

---

## 1. Download the APK directly from GitHub
Open the **Releases** section on the right side of this GitHub repository and tap **`RunStats.apk`** to download and install it directly on your Android phone (no unzipping needed).

---

## 2. Connect Your Garmin Runs (100% Free — Two Options)

### Option A: Automatic Garmin Sync via Intervals.icu (Recommended — Free)
1. Create a free account at [intervals.icu](https://intervals.icu/).
2. In Intervals.icu go to **Settings → Connections → Garmin Connect** and authorize your Garmin account (you can also click **Import All Data** in Intervals.icu to backfill past years from Garmin).
3. Scroll to **Developer Settings** at the bottom of **Settings** on Intervals.icu and generate/copy your **API Key**.
4. Open **RunStats** → **Settings** → paste your **Intervals.icu API Key** → tap **Save Key & Sync Full History**.

### Option B: Direct Offline Garmin Connect CSV Import
1. Go to [connect.garmin.com](https://connect.garmin.com/) → **Activities → All Activities** → scroll down as far as you want → click **Export CSV**.
2. Open **RunStats** → **Settings** → tap **Import Garmin Activities.csv** and pick the file.

---

## 3. Optional: Enable AI Running Coach
1. Get a free personal Gemini API key at [aistudio.google.com](https://aistudio.google.com/).
2. Open **Settings** in RunStats → paste your **Gemini API Key** → tap **Save AI Settings**.
3. Open the **AI Coach** tab and tap a quick prompt or ask your own question!

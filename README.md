# Stravo (Android / Java)

A personal Strava-inspired (blue theme) native Android running stats, multi-year charts, activity feed, and Gemini Pro AI running coach app built in **Java** (`MainActivity.java`) and compiled automatically via **GitHub Actions**.

Syncs your **Garmin Connect** runs **100% free** via **Intervals.icu** (no Strava subscription required) OR imports Garmin Connect `Activities.csv` files directly offline.

## Features
- **Progress (Dashboard):** Strava-style Weekly Snapshot (`Runs | Time | Distance`), yearly/30-day/all-time totals, weekly streaks, Acute:Chronic Workload Ratio (ACWR), aerobic efficiency trends, and period highlights.
- **Charts (MPAndroidChart):** Yearly distance bar chart, cumulative year-over-year comparison lines, 12-month volume, and Avg Pace vs. Avg Heart Rate trend.
- **Feed:** Strava-style activity feed with search, year filters, and full run breakdown on tap.
- **AI Coach:** Powered by Google's flagship **`gemini-pro-latest`** by default (customizable in Settings), grounded directly in your real multi-year & recent running metrics.
- **Units Toggle:** Switch cleanly between **Kilometers (`km` / `m`)** and **Miles (`mi` / `ft`)** in **You & Settings**.

---

## 1. Download `Stravo.apk` directly from GitHub
Open the **Releases** section on the right side of this GitHub repository and tap **`Stravo.apk`** to install it directly on any Android phone.

---

## 2. Connect Your Garmin Runs (100% Free)

### Option A: Automatic Garmin Sync via Intervals.icu (Recommended — Free)
1. Create a free account at [intervals.icu](https://intervals.icu/).
2. Go to **Settings → Connections → Garmin Connect** and authorize Garmin (click **Import All Data** to backfill past years).
3. Scroll to **Developer Settings** at the bottom of **Settings** on Intervals.icu and copy your **API Key**.
4. Open **Stravo → You → Settings**, paste your **Intervals.icu API Key**, and tap **Save Key & Sync Full History**.

### Option B: Direct Offline Garmin Connect CSV Import
1. Go to [connect.garmin.com](https://connect.garmin.com/) → **Activities → All Activities** → **Export CSV**.
2. Open **Stravo → You → Settings** → tap **Import Garmin Activities.csv**.

---

## 3. Optional: Enable AI Running Coach
1. Get a personal Gemini API key at [aistudio.google.com](https://aistudio.google.com/).
2. Open **You & Settings** in Stravo → paste your **Gemini API Key** → tap **Save AI Settings**.
3. Defaults to **`gemini-pro-latest`** (you can switch to `gemini-flash-latest` anytime if you want faster/higher free-tier quota responses).

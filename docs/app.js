(() => {
  const METERS_PER_KM = 1000;
  const METERS_PER_MILE = 1609.344;
  const DEFAULT_GEMINI_MODEL = "gemini-pro-latest";
  const STORAGE_KEYS = {
    runs: "stravo_runs_v1",
    wellness: "stravo_wellness_v1",
    prefs: "stravo_prefs_v1"
  };

  const state = {
    runs: [],
    wellness: [],
    prefs: {
      useMiles: false,
      darkMode: window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches,
      intervalsKey: "",
      athleteName: "",
      lastSyncMs: 0,
      geminiKey: "",
      geminiModel: DEFAULT_GEMINI_MODEL
    },
    period: "year",
    chartMonths: 12,
    feedYear: "all",
    feedSearch: "",
    chat: [],
    isSyncing: false,
    isSendingChat: false,
    charts: {}
  };

  const $ = (id) => document.getElementById(id);

  // ---------- Storage ----------
  function loadState() {
    try {
      const savedPrefs = JSON.parse(localStorage.getItem(STORAGE_KEYS.prefs) || "null");
      if (savedPrefs) state.prefs = { ...state.prefs, ...savedPrefs };
      if (!state.prefs.geminiModel) state.prefs.geminiModel = DEFAULT_GEMINI_MODEL;

      const savedRuns = JSON.parse(localStorage.getItem(STORAGE_KEYS.runs) || "[]");
      if (Array.isArray(savedRuns)) state.runs = savedRuns;

      const savedWell = JSON.parse(localStorage.getItem(STORAGE_KEYS.wellness) || "[]");
      if (Array.isArray(savedWell)) state.wellness = savedWell;
    } catch (e) {
      console.warn("Could not load local storage:", e);
    }
  }

  function savePrefs() {
    localStorage.setItem(STORAGE_KEYS.prefs, JSON.stringify(state.prefs));
  }

  function saveData() {
    state.runs.sort((a, b) => b.startEpoch - a.startEpoch);
    state.wellness.sort((a, b) => (a.date < b.date ? 1 : -1));
    localStorage.setItem(STORAGE_KEYS.runs, JSON.stringify(state.runs));
    localStorage.setItem(STORAGE_KEYS.wellness, JSON.stringify(state.wellness));
  }

  function toast(msg) {
    const el = $("toast");
    el.textContent = msg;
    el.classList.remove("hidden");
    clearTimeout(toast._t);
    toast._t = setTimeout(() => el.classList.add("hidden"), 3400);
  }

  // ---------- Formatting ----------
  function distInUnit(meters) {
    return meters / (state.prefs.useMiles ? METERS_PER_MILE : METERS_PER_KM);
  }

  function unitLabel() {
    return state.prefs.useMiles ? "mi" : "km";
  }

  function fmtDistance(meters) {
    const val = distInUnit(meters);
    if (val >= 1000) return `${Math.round(val).toLocaleString()} ${unitLabel()}`;
    return `${val.toFixed(1)} ${unitLabel()}`;
  }

  function fmtPace(secPerUnit) {
    if (!secPerUnit || !isFinite(secPerUnit) || secPerUnit <= 0) return "--:--";
    const total = Math.round(secPerUnit);
    const m = Math.floor(total / 60);
    const s = total % 60;
    return `${m}:${String(s).padStart(2, "0")} /${unitLabel()}`;
  }

  function fmtPaceShort(minPerUnit) {
    if (!minPerUnit || !isFinite(minPerUnit) || minPerUnit <= 0) return "";
    const total = Math.round(minPerUnit * 60);
    const m = Math.floor(total / 60);
    const s = total % 60;
    return `${m}:${String(s).padStart(2, "0")}`;
  }

  function fmtDuration(sec) {
    if (!sec || sec <= 0) return "0m";
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = Math.floor(sec % 60);
    if (h >= 10) return `${h}h ${String(m).padStart(2, "0")}m`;
    if (h > 0) return `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
    return `${m}:${String(s).padStart(2, "0")}`;
  }

  function fmtElevation(meters) {
    if (state.prefs.useMiles) return `${Math.round(meters * 3.28084).toLocaleString()} ft`;
    return `${Math.round(meters).toLocaleString()} m`;
  }

  function fmtHr(bpm) {
    return bpm > 40 ? `${Math.round(bpm)} bpm` : "--";
  }

  function fmtDate(ymd) {
    if (!ymd) return "";
    const d = new Date(ymd + "T12:00:00");
    return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
  }

  function fmtTimestamp(ms) {
    if (!ms) return "Never";
    return new Date(ms).toLocaleString("en-US", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  }

  function runEfficiency(r) {
    if (!r.avgHr || r.avgHr <= 40 || !r.movingTime || r.movingTime <= 60 || !r.distance || r.distance <= 100) return 0;
    return r.distance / (r.avgHr * (r.movingTime / 60));
  }

  function runPaceSec(r, useMiles = state.prefs.useMiles) {
    const dist = r.distance / (useMiles ? METERS_PER_MILE : METERS_PER_KM);
    return dist > 0.01 && r.movingTime > 0 ? r.movingTime / dist : 0;
  }

  function todayYmd() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  function daysAgoYmd(days) {
    const d = new Date();
    d.setDate(d.getDate() - days);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  function mondayOf(ymd) {
    const d = new Date(ymd + "T12:00:00");
    const day = d.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    d.setDate(d.getDate() + diff);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  function initialsOf(name) {
    const clean = (name || "Stravo Runner").trim();
    const parts = clean.split(/\s+/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return clean[0].toUpperCase();
  }

  // ---------- Analytics ----------
  function computeSummary(runs) {
    const s = { count: 0, dist: 0, moving: 0, elev: 0, hrSum: 0, hrCount: 0, longest: null, fastest: null, climb: null };
    for (const r of runs) {
      s.count++;
      s.dist += r.distance;
      s.moving += r.movingTime;
      s.elev += r.elevation;
      if (r.avgHr > 40) { s.hrSum += r.avgHr; s.hrCount++; }
      if (!s.longest || r.distance > s.longest.distance) s.longest = r;
      if (r.distance >= 4800 && r.movingTime > 0) {
        if (!s.fastest || runPaceSec(r, false) < runPaceSec(s.fastest, false)) s.fastest = r;
      }
      if (!s.climb || r.elevation > s.climb.elevation) s.climb = r;
    }
    s.avgHr = s.hrCount > 0 ? s.hrSum / s.hrCount : 0;
    const distUnits = distInUnit(s.dist);
    s.avgPaceSec = distUnits > 0.01 && s.moving > 0 ? s.moving / distUnits : 0;
    return s;
  }

  function computePersonalBests(runs) {
    const targets = [
      { label: "5K", minM: 4900, targetM: 5000 },
      { label: "10K", minM: 9800, targetM: 10000 },
      { label: "Half Marathon", minM: 20900, targetM: 21097.5 },
      { label: "Marathon", minM: 41800, targetM: 42195 }
    ];
    const out = [];
    for (const t of targets) {
      let best = null;
      let bestPace = Infinity;
      for (const r of runs) {
        if (r.distance >= t.minM && r.movingTime > 60) {
          const p = runPaceSec(r, false);
          if (p > 150 && p < bestPace) { bestPace = p; best = r; }
        }
      }
      if (best) {
        out.push({
          label: t.label,
          estSec: Math.round(bestPace * (t.targetM / 1000)),
          paceSecPerKm: bestPace,
          run: best
        });
      }
    }
    return out;
  }

  function computeInsights(runs) {
    const d7 = daysAgoYmd(6);
    const d28 = daysAgoYmd(27);
    const d90 = daysAgoYmd(90);
    const d180 = daysAgoYmd(180);
    const today = todayYmd();

    let last7 = 0, last28 = 0, effRecSum = 0, effRecN = 0, effOldSum = 0, effOldN = 0;
    const weeks = new Set();

    for (const r of runs) {
      weeks.add(mondayOf(r.date));
      if (r.date >= d7 && r.date <= today) last7 += r.distance;
      if (r.date >= d28 && r.date <= today) last28 += r.distance;
      const eff = runEfficiency(r);
      if (eff > 0) {
        if (r.date >= d90) { effRecSum += eff; effRecN++; }
        else if (r.date >= d180) { effOldSum += eff; effOldN++; }
      }
    }

    const wkAvg28 = last28 / 4;
    const acwr = wkAvg28 > 500 ? last7 / wkAvg28 : 0;
    const recEff = effRecN > 0 ? effRecSum / effRecN : 0;
    const oldEff = effOldN > 0 ? effOldSum / effOldN : 0;
    const effDeltaPct = recEff > 0 && oldEff > 0 ? ((recEff - oldEff) / oldEff) * 100 : 0;

    // Weekly streaks
    const sortedWks = Array.from(weeks).sort();
    let longest = 0, curRun = 0, prevMs = null;
    for (const wk of sortedWks) {
      const ms = new Date(wk + "T12:00:00").getTime();
      if (prevMs !== null && Math.round((ms - prevMs) / 86400000) === 7) curRun++;
      else curRun = 1;
      if (curRun > longest) longest = curRun;
      prevMs = ms;
    }

    const thisMon = mondayOf(today);
    const prevMon = mondayOf(daysAgoYmd(7));
    let cursor = weeks.has(thisMon) ? thisMon : prevMon;
    let currentStreak = 0;
    while (weeks.has(cursor)) {
      currentStreak++;
      const d = new Date(cursor + "T12:00:00");
      d.setDate(d.getDate() - 7);
      cursor = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    }

    return { last7, wkAvg28, acwr, recEff, oldEff, effDeltaPct, currentStreak, longestStreak: longest };
  }

  function computeHrZoneComparison(runs) {
    const bands = [
      { label: "Easy (<135 bpm)", min: 40, max: 135, rN: 0, rDist: 0, rSec: 0, oN: 0, oDist: 0, oSec: 0 },
      { label: "Steady (135–149 bpm)", min: 135, max: 150, rN: 0, rDist: 0, rSec: 0, oN: 0, oDist: 0, oSec: 0 },
      { label: "Tempo (150–164 bpm)", min: 150, max: 165, rN: 0, rDist: 0, rSec: 0, oN: 0, oDist: 0, oSec: 0 },
      { label: "Hard (165+ bpm)", min: 165, max: 300, rN: 0, rDist: 0, rSec: 0, oN: 0, oDist: 0, oSec: 0 }
    ];
    const cutoff = daysAgoYmd(90);
    for (const r of runs) {
      if (r.avgHr <= 40 || r.distance < 1500 || r.movingTime <= 60) continue;
      const b = bands.find((x) => r.avgHr >= x.min && r.avgHr < x.max);
      if (!b) continue;
      if (r.date >= cutoff) { b.rN++; b.rDist += r.distance; b.rSec += r.movingTime; }
      else { b.oN++; b.oDist += r.distance; b.oSec += r.movingTime; }
    }
    return bands
      .filter((b) => b.rN > 0 || b.oN > 0)
      .map((b) => ({
        label: b.label,
        recentN: b.rN,
        recentPaceKm: b.rDist > 100 ? b.rSec / (b.rDist / 1000) : 0,
        olderN: b.oN,
        olderPaceKm: b.oDist > 100 ? b.oSec / (b.oDist / 1000) : 0
      }));
  }

  function computeWellnessSummary(entries) {
    const s = { vo2: 0, vo2Date: "", rhr: 0, avg30Rhr: 0, hrv: 0, avg30Hrv: 0, sleepScore: 0, avg30SleepHrs: 0, count: entries.length };
    const c30 = daysAgoYmd(30);
    let rhrSum = 0, rhrN = 0, hrvSum = 0, hrvN = 0, slSum = 0, slN = 0;
    for (const w of entries) {
      if (!s.vo2 && w.vo2max > 0) { s.vo2 = w.vo2max; s.vo2Date = w.date; }
      if (!s.rhr && w.restingHr > 25) s.rhr = w.restingHr;
      if (!s.hrv && w.hrv > 0) s.hrv = w.hrv;
      if (!s.sleepScore && w.sleepScore > 0) s.sleepScore = w.sleepScore;
      if (w.date >= c30) {
        if (w.restingHr > 25) { rhrSum += w.restingHr; rhrN++; }
        if (w.hrv > 0) { hrvSum += w.hrv; hrvN++; }
        if (w.sleepSecs > 3600) { slSum += w.sleepSecs / 3600; slN++; }
      }
    }
    if (rhrN) s.avg30Rhr = rhrSum / rhrN;
    if (hrvN) s.avg30Hrv = hrvSum / hrvN;
    if (slN) s.avg30SleepHrs = slSum / slN;
    return s;
  }

  function buildMonthKeys(items, maxMonths) {
    const now = new Date();
    let startYear = now.getFullYear();
    let startMonth = now.getMonth(); // 0..11
    if (maxMonths > 0) {
      const d = new Date(now.getFullYear(), now.getMonth() - (maxMonths - 1), 1);
      startYear = d.getFullYear();
      startMonth = d.getMonth();
    } else if (items.length > 0) {
      let oldest = items[items.length - 1].date;
      for (const it of items) {
        if (it.date && it.date < oldest) oldest = it.date;
      }
      if (oldest && oldest.length >= 7) {
        startYear = parseInt(oldest.slice(0, 4), 10);
        startMonth = parseInt(oldest.slice(5, 7), 10) - 1;
      }
      const cap = new Date(now.getFullYear(), now.getMonth() - 71, 1);
      if (new Date(startYear, startMonth, 1) < cap) {
        startYear = cap.getFullYear();
        startMonth = cap.getMonth();
      }
    } else {
      const d = new Date(now.getFullYear(), now.getMonth() - 11, 1);
      startYear = d.getFullYear();
      startMonth = d.getMonth();
    }

    const keys = [];
    const cursor = new Date(startYear, startMonth, 1);
    const end = new Date(now.getFullYear(), now.getMonth(), 1);
    while (cursor <= end) {
      const ym = `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, "0")}`;
      const label = cursor.toLocaleDateString("en-US", { month: "short", year: "2-digit" });
      keys.push({ ym, label });
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return keys;
  }

  function computeMonthlyRuns(runs, maxMonths) {
    const keys = buildMonthKeys(runs, maxMonths);
    const map = new Map(keys.map((k) => [k.ym, { ...k, dist: 0, moving: 0, hrSum: 0, hrSec: 0, effSum: 0, effSec: 0, count: 0 }]));
    for (const r of runs) {
      const mp = map.get(r.date.slice(0, 7));
      if (!mp) continue;
      mp.count++;
      mp.dist += r.distance;
      mp.moving += r.movingTime;
      if (r.avgHr > 40 && r.movingTime > 0) { mp.hrSum += r.avgHr * r.movingTime; mp.hrSec += r.movingTime; }
      const eff = runEfficiency(r);
      if (eff > 0 && r.movingTime > 0) { mp.effSum += eff * r.movingTime; mp.effSec += r.movingTime; }
    }
    return Array.from(map.values());
  }

  function computeMonthlyWellness(entries, maxMonths) {
    const keys = buildMonthKeys(entries, maxMonths);
    const map = new Map(keys.map((k) => [k.ym, { ...k, vo2Sum: 0, vo2N: 0, rhrSum: 0, rhrN: 0 }]));
    for (const w of entries) {
      const mp = map.get(w.date.slice(0, 7));
      if (!mp) continue;
      if (w.vo2max > 15) { mp.vo2Sum += w.vo2max; mp.vo2N++; }
      if (w.restingHr > 25) { mp.rhrSum += w.restingHr; mp.rhrN++; }
    }
    return Array.from(map.values());
  }

  // ---------- Intervals.icu API (CORS-enabled /api/v1/) ----------
  async function intervalsGet(path, apiKey) {
    const auth = "Basic " + btoa("API_KEY:" + apiKey.trim());
    const resp = await fetch(`https://intervals.icu/api/v1/athlete/0${path}`, {
      headers: { Authorization: auth, Accept: "application/json" }
    });
    if (resp.status === 401 || resp.status === 403) {
      throw new Error("Invalid Intervals.icu API key. Check Settings → Developer Settings on intervals.icu.");
    }
    if (!resp.ok) {
      throw new Error(`Intervals.icu HTTP ${resp.status}`);
    }
    return resp.json();
  }

  function isRunType(t) {
    const n = (t || "").toLowerCase();
    return n.includes("run") || n.includes("treadmill");
  }

  async function syncIntervals(fullHistory, onProgress) {
    const key = state.prefs.intervalsKey.trim();
    if (!key) {
      toast("Paste your free Intervals.icu API key in You → Settings first.");
      switchTab("settings");
      return;
    }
    if (state.isSyncing) return;
    state.isSyncing = true;
    setSyncButtonsBusy(true);

    try {
      onProgress?.("Connecting to Intervals.icu...");
      const profile = await intervalsGet("", key);
      const name = (profile.name || `${profile.firstname || ""} ${profile.lastname || ""}`).trim();
      if (name) state.prefs.athleteName = name;

      const today = todayYmd();
      const endYear = new Date().getFullYear();

      const runMap = new Map(state.runs.filter((r) => !r.isSample).map((r) => [String(r.id), r]));
      const wellMap = new Map(state.wellness.filter((w) => !w.isSample).map((w) => [w.date, w]));

      let runsAdded = 0;
      let wellAdded = 0;

      const newestRealRun = state.runs.find((r) => !r.isSample);
      if (!fullHistory && newestRealRun) {
        const d = new Date(newestRealRun.date + "T12:00:00");
        d.setDate(d.getDate() - 3);
        const oldest = d.toISOString().slice(0, 10);
        onProgress?.(`Syncing runs since ${oldest}...`);
        const acts = await intervalsGet(`/activities?oldest=${oldest}&newest=${today}`, key);
        for (const item of acts) {
          const parsed = parseIntervalsActivity(item);
          if (parsed) { runMap.set(String(parsed.id), parsed); runsAdded++; }
        }
        onProgress?.("Syncing Garmin wellness...");
        try {
          const well = await intervalsGet(`/wellness?oldest=${daysAgoYmd(30)}&newest=${today}`, key);
          for (const w of well) {
            const pw = parseIntervalsWellness(w);
            if (pw) { wellMap.set(pw.date, pw); wellAdded++; }
          }
        } catch (ignored) {}
      } else {
        for (let yr = endYear; yr >= 2012; yr -= 2) {
          const chunkStart = Math.max(2012, yr - 1);
          const oldest = `${chunkStart}-01-01`;
          const newest = yr === endYear ? today : `${yr}-12-31`;
          onProgress?.(`Syncing runs ${chunkStart}–${yr} (${runsAdded} found)...`);
          const acts = await intervalsGet(`/activities?oldest=${oldest}&newest=${newest}`, key);
          for (const item of acts) {
            const parsed = parseIntervalsActivity(item);
            if (parsed) { runMap.set(String(parsed.id), parsed); runsAdded++; }
          }
        }
        for (let yr = endYear; yr >= 2016; yr -= 2) {
          const chunkStart = Math.max(2016, yr - 1);
          const oldest = `${chunkStart}-01-01`;
          const newest = yr === endYear ? today : `${yr}-12-31`;
          onProgress?.(`Syncing Garmin health ${chunkStart}–${yr}...`);
          try {
            const well = await intervalsGet(`/wellness?oldest=${oldest}&newest=${newest}`, key);
            for (const w of well) {
              const pw = parseIntervalsWellness(w);
              if (pw) { wellMap.set(pw.date, pw); wellAdded++; }
            }
          } catch (ignored) {}
        }
      }

      if (runMap.size > 0) state.runs = Array.from(runMap.values());
      if (wellMap.size > 0) state.wellness = Array.from(wellMap.values());
      state.prefs.lastSyncMs = Date.now();
      savePrefs();
      saveData();

      const msg = `Synced ${runsAdded} runs${wellAdded > 0 ? ` & ${wellAdded} health days` : ""} from Intervals.icu!`;
      onProgress?.(msg);
      toast(msg);
      renderAll();
    } catch (err) {
      const msg = `Sync failed: ${err.message}`;
      onProgress?.(msg);
      toast(msg);
    } finally {
      state.isSyncing = false;
      setSyncButtonsBusy(false);
    }
  }

  function parseIntervalsActivity(item) {
    const distance = Number(item.distance) || 0;
    const movingTime = Number(item.moving_time) || 0;
    if (distance <= 10 && movingTime <= 10) return null;
    const type = item.type || "";
    if (!isRunType(type)) return null;

    const dateLocal = item.start_date_local || item.start_date || "";
    const date = dateLocal.slice(0, 10);
    if (date.length !== 10) return null;
    const epoch = item.start_date
      ? Math.floor(new Date(item.start_date).getTime() / 1000)
      : Math.floor(new Date(date + "T07:00:00").getTime() / 1000);

    return {
      id: String(item.id || `${date}_${Math.round(distance)}`),
      name: (item.name || type).trim(),
      sportType: type,
      date,
      startEpoch: epoch,
      distance,
      movingTime,
      elapsedTime: Number(item.elapsed_time) || movingTime,
      elevation: Number(item.total_elevation_gain) || 0,
      avgHr: Number(item.average_heartrate) || 0,
      maxHr: Number(item.max_heartrate) || 0,
      avgCadence: Number(item.average_cadence) || 0,
      load: Number(item.icu_training_load) || 0,
      isSample: false
    };
  }

  function parseIntervalsWellness(item) {
    const date = (item.id || "").slice(0, 10);
    if (date.length !== 10) return null;
    const w = {
      date,
      vo2max: Number(item.vo2max) || 0,
      restingHr: Number(item.restingHR) || 0,
      hrv: Number(item.hrv) || 0,
      sleepSecs: Number(item.sleepSecs) || 0,
      sleepScore: Number(item.sleepScore) || 0,
      weight: Number(item.weight) || 0,
      isSample: false
    };
    return (w.vo2max || w.restingHr || w.hrv || w.sleepSecs || w.sleepScore || w.weight) ? w : null;
  }

  function setSyncButtonsBusy(busy) {
    ["btnTopSync", "btnDashSync", "btnSaveFullSync", "btnSyncNew"].forEach((id) => {
      const b = $(id);
      if (b) b.disabled = busy;
    });
  }

  // ---------- Garmin CSV Import ----------
  function parseCsvLine(line) {
    const out = [];
    let cur = "";
    let inQ = false;
    for (let i = 0; i < line.length; i++) {
      const c = line[i];
      if (c === '"') {
        if (inQ && line[i + 1] === '"') { cur += '"'; i++; }
        else inQ = !inQ;
      } else if (c === "," && !inQ) {
        out.push(cur);
        cur = "";
      } else {
        cur += c;
      }
    }
    out.push(cur);
    return out;
  }

  function parseDurationToSec(raw) {
    if (!raw || raw === "--") return 0;
    const clean = raw.trim().split(".")[0];
    const parts = clean.split(":").map((x) => parseInt(x, 10));
    if (parts.some(isNaN)) return 0;
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    return 0;
  }

  async function importGarminCsv(file, csvInMiles) {
    const text = (await file.text()).replace(/^\uFEFF/, "");
    const lines = text.split(/\r?\n/).filter((l) => l.trim());
    if (lines.length < 2) return 0;

    const headers = parseCsvLine(lines[0]).map((h) => h.trim().toLowerCase());
    const idxOf = (...names) => {
      for (const n of names) {
        const idx = headers.indexOf(n);
        if (idx !== -1) return idx;
      }
      return -1;
    };

    const iType = idxOf("activity type", "type");
    const iDate = idxOf("date", "start time");
    const iTitle = idxOf("title", "name");
    const iDist = idxOf("distance");
    const iMov = idxOf("moving time", "time");
    const iElap = idxOf("elapsed time", "time");
    const iHr = idxOf("avg hr", "average heart rate");
    const iMaxHr = idxOf("max hr", "max heart rate");
    const iElev = idxOf("total ascent", "elevation gain", "elev gain");
    const iCad = idxOf("avg run cadence", "avg cadence");

    const num = (v) => {
      if (!v || v === "--") return 0;
      const n = parseFloat(v.replace(/,/g, "").replace(/[^0-9.-]/g, ""));
      return isFinite(n) ? n : 0;
    };

    const runMap = new Map(state.runs.filter((r) => !r.isSample).map((r) => [String(r.id), r]));
    let added = 0;

    for (let i = 1; i < lines.length; i++) {
      const f = parseCsvLine(lines[i]);
      const type = iType >= 0 ? (f[iType] || "").trim() : "Run";
      if (!isRunType(type)) continue;

      const rawDist = iDist >= 0 ? num(f[iDist]) : 0;
      if (rawDist <= 0) continue;

      const dateRaw = iDate >= 0 ? (f[iDate] || "").trim() : "";
      const date = dateRaw.slice(0, 10);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) continue;

      const distMeters = rawDist * (csvInMiles ? METERS_PER_MILE : METERS_PER_KM);
      let moving = iMov >= 0 ? parseDurationToSec(f[iMov]) : 0;
      let elapsed = iElap >= 0 ? parseDurationToSec(f[iElap]) : moving;
      if (!moving) moving = elapsed;
      if (!elapsed) elapsed = moving;

      const elevRaw = iElev >= 0 ? num(f[iElev]) : 0;
      const elevM = csvInMiles ? elevRaw / 3.28084 : elevRaw;
      const title = (iTitle >= 0 ? (f[iTitle] || "").trim() : "") || type;
      const id = `csv_${date}_${Math.round(distMeters)}_${title.slice(0, 16)}`;

      runMap.set(id, {
        id,
        name: title,
        sportType: type,
        date,
        startEpoch: Math.floor(new Date(dateRaw.replace(" ", "T")).getTime() / 1000) || Math.floor(new Date(date + "T07:00:00").getTime() / 1000),
        distance: distMeters,
        movingTime: moving,
        elapsedTime: elapsed,
        elevation: elevM,
        avgHr: iHr >= 0 ? num(f[iHr]) : 0,
        maxHr: iMaxHr >= 0 ? num(f[iMaxHr]) : 0,
        avgCadence: iCad >= 0 ? num(f[iCad]) : 0,
        load: 0,
        isSample: false
      });
      added++;
    }

    if (added > 0) {
      state.runs = Array.from(runMap.values());
      state.prefs.lastSyncMs = Date.now();
      savePrefs();
      saveData();
      renderAll();
    }
    return added;
  }

  // ---------- Sample Data ----------
  function seedSampleData() {
    const runs = [];
    const wellness = [];
    let seed = 42;
    const rand = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };

    const easyNames = ["Morning Easy Run", "Swansea Bay Loop", "Recovery Jog", "Park Shakeout", "Aerobic Base Run"];
    const tempoNames = ["Threshold Tempo", "Cruise Intervals", "Progression Run", "Fast Finish 10K"];
    const longNames = ["Sunday Long Run", "Coastal Path Long Run", "Endurance Builder", "Half Marathon Prep"];

    const today = new Date();
    const start = new Date(today.getFullYear() - 3, today.getMonth(), today.getDate() + 7);
    const totalDays = Math.max(1, Math.round((today - start) / 86400000));
    let idCounter = -1;

    for (let d = new Date(start), idx = 0; d <= today; d.setDate(d.getDate() + 1), idx++) {
      const ymd = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
      const p = idx / totalDays;

      wellness.push({
        date: ymd,
        vo2max: +(48.0 + 5.2 * p + (rand() - 0.5) * 0.8).toFixed(1),
        restingHr: Math.round(55 - 6 * p + (rand() - 0.5) * 3),
        hrv: Math.round(52 + 14 * p + (rand() - 0.5) * 10),
        sleepSecs: Math.round((7.3 + (rand() - 0.5) * 1.2) * 3600),
        sleepScore: Math.round(78 + (rand() - 0.5) * 18),
        weight: +(76 - 2 * p).toFixed(1),
        isSample: true
      });

      const dow = d.getDay(); // 0=Sun..6=Sat
      let shouldRun = false;
      let kind = "easy";
      if (dow === 2 || dow === 4 || dow === 6) {
        shouldRun = rand() < 0.78;
        kind = dow === 4 && rand() < 0.55 ? "tempo" : "easy";
      } else if (dow === 0) {
        shouldRun = rand() < 0.82;
        kind = "long";
      } else if (dow === 3) {
        shouldRun = rand() < 0.35;
      }

      if (shouldRun) {
        const basePace = 355 - 28 * p;
        const baseHr = 148 - 10 * p;
        let distKm, pace, hr, elev, name, sportType = "Run";
        if (kind === "long") {
          distKm = 13 + rand() * 9.5 + 2.5 * p;
          pace = basePace + 8 + (rand() - 0.5) * 14;
          hr = baseHr + 4 + (rand() - 0.5) * 8;
          elev = distKm * (7 + rand() * 8);
          name = longNames[Math.floor(rand() * longNames.length)];
          if (rand() < 0.3) { sportType = "TrailRun"; elev *= 1.7; pace += 20; }
        } else if (kind === "tempo") {
          distKm = 5.1 + rand() * 6;
          pace = basePace - 38 + (rand() - 0.5) * 12;
          hr = baseHr + 18 + (rand() - 0.5) * 7;
          elev = distKm * (4 + rand() * 5);
          name = tempoNames[Math.floor(rand() * tempoNames.length)];
        } else {
          distKm = 5.2 + rand() * 5.2;
          pace = basePace + (rand() - 0.5) * 18;
          hr = baseHr + (rand() - 0.5) * 9;
          elev = distKm * (4.5 + rand() * 5.5);
          name = easyNames[Math.floor(rand() * easyNames.length)];
        }
        const moving = Math.round(distKm * pace);
        runs.push({
          id: String(idCounter--),
          name,
          sportType,
          date: ymd,
          startEpoch: Math.floor(new Date(ymd + "T07:15:00").getTime() / 1000),
          distance: distKm * 1000,
          movingTime: moving,
          elapsedTime: moving + Math.floor(rand() * 90),
          elevation: elev,
          avgHr: hr,
          maxHr: Math.min(196, hr + 12 + rand() * 10),
          avgCadence: 84 + rand() * 6,
          load: Math.max(15, (moving / 60) * ((hr - 105) / 35)),
          isSample: true
        });
      }
    }

    state.runs = runs;
    state.wellness = wellness;
    saveData();
    renderAll();
    toast(`Loaded ${runs.length} sample runs & 3 years of health data!`);
  }

  // ---------- Gemini AI Coach ----------
  function buildCoachContext() {
    const u = unitLabel();
    const lines = [
      "You are an experienced, supportive, evidence-based running coach analyzing the user's real Garmin running history & health metrics.",
      `Give concise, practical, personalized advice grounded directly in their numbers below. Use ${u} and min/${u}.`,
      "Do not diagnose medical conditions; suggest seeing a professional if they mention sharp or persistent pain.",
      ""
    ];
    if (!state.runs.length) {
      lines.push("ATHLETE DATA: No runs synced yet.");
      return lines.join("\n");
    }

    const all = computeSummary(state.runs);
    const ins = computeInsights(state.runs);
    lines.push("=== ALL-TIME SUMMARY ===");
    lines.push(`Total Runs: ${all.count} | Distance: ${fmtDistance(all.dist)} | Avg Pace: ${fmtPace(all.avgPaceSec)} | Avg HR: ${fmtHr(all.avgHr)} | Elev: ${fmtElevation(all.elev)}`);

    const prs = computePersonalBests(state.runs);
    if (prs.length) {
      lines.push("\n=== ESTIMATED PERSONAL BESTS ===");
      for (const pr of prs) {
        const paceU = state.prefs.useMiles ? pr.paceSecPerKm * 1.609344 : pr.paceSecPerKm;
        lines.push(`${pr.label}: ${fmtDuration(pr.estSec)} (${fmtPace(paceU)}) on ${pr.run.date}`);
      }
    }

    lines.push("\n=== TRAINING LOAD & HR-VS-PACE EFFICIENCY ===");
    lines.push(`Last 7d: ${fmtDistance(ins.last7)} | 28d Weekly Avg: ${fmtDistance(ins.wkAvg28)} | ACWR: ${ins.acwr.toFixed(2)}`);
    lines.push(`Weekly Streak: ${ins.currentStreak} weeks current (${ins.longestStreak} longest)`);
    if (ins.recEff > 0) {
      lines.push(`Aerobic Efficiency (90d): ${ins.recEff.toFixed(2)} m/beat (${ins.effDeltaPct >= 0 ? "+" : ""}${ins.effDeltaPct.toFixed(1)}% vs prior 90d)`);
    }

    const zones = computeHrZoneComparison(state.runs);
    if (zones.length) {
      lines.push("\n=== PACE BY HEART RATE ZONE (RECENT 90D VS PRIOR) ===");
      const f = state.prefs.useMiles ? 1.609344 : 1;
      for (const z of zones) {
        lines.push(`${z.label}: Recent 90d ${z.recentN ? fmtPace(z.recentPaceKm * f) : "N/A"} (${z.recentN}) vs Prior ${z.olderN ? fmtPace(z.olderPaceKm * f) : "N/A"} (${z.olderN})`);
      }
    }

    const ws = computeWellnessSummary(state.wellness);
    if (ws.count > 0) {
      lines.push("\n=== GARMIN WELLNESS ===");
      if (ws.vo2) lines.push(`VO2 Max: ${ws.vo2.toFixed(1)} (${ws.vo2Date})`);
      if (ws.rhr) lines.push(`Resting HR: ${Math.round(ws.rhr)} bpm (30d avg: ${Math.round(ws.avg30Rhr || ws.rhr)} bpm)`);
      if (ws.hrv) lines.push(`HRV: ${Math.round(ws.hrv)} ms (30d avg: ${Math.round(ws.avg30Hrv || ws.hrv)} ms)`);
      if (ws.sleepScore || ws.avg30SleepHrs) lines.push(`Sleep: score ${Math.round(ws.sleepScore)}, 30d avg ${ws.avg30SleepHrs.toFixed(1)}h`);
    }

    lines.push("\n=== YEARLY BREAKDOWN ===");
    const byYear = new Map();
    for (const r of state.runs) {
      const y = r.date.slice(0, 4);
      if (!byYear.has(y)) byYear.set(y, []);
      byYear.get(y).push(r);
    }
    for (const y of Array.from(byYear.keys()).sort()) {
      const ys = computeSummary(byYear.get(y));
      lines.push(`${y}: ${ys.count} runs, ${fmtDistance(ys.dist)}, avg pace ${fmtPace(ys.avgPaceSec)}, avg HR ${fmtHr(ys.avgHr)}`);
    }

    lines.push("\n=== RECENT 15 RUNS ===");
    for (const r of state.runs.slice(0, 15)) {
      lines.push(`${r.date} | ${r.name} | ${fmtDistance(r.distance)} | ${fmtPace(runPaceSec(r))} | HR: ${fmtHr(r.avgHr)} | Eff: ${runEfficiency(r).toFixed(2)} m/beat`);
    }
    return lines.join("\n");
  }

  async function callGeminiOnce(apiKey, model, payload) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 25000);
    try {
      const resp = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify(payload),
        signal: controller.signal
      });
      const json = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        const msg = json?.error?.message || `HTTP ${resp.status}`;
        const err = new Error(`Gemini API error (${resp.status}): ${msg}`);
        err.retryable = [404, 429, 500, 503].includes(resp.status);
        throw err;
      }
      const parts = json?.candidates?.[0]?.content?.parts || [];
      const text = parts.map((p) => p.text || "").join("\n").trim();
      if (!text) throw new Error("Gemini returned an empty response.");
      return text;
    } catch (e) {
      if (e.name === "AbortError") {
        const err = new Error(`Timed out waiting for ${model}.`);
        err.retryable = true;
        throw err;
      }
      throw e;
    } finally {
      clearTimeout(timer);
    }
  }

  async function askGeminiCoach(question) {
    const key = state.prefs.geminiKey.trim();
    if (!key) {
      toast("Add your Gemini API key in You → Settings first.");
      switchTab("settings");
      return;
    }
    if (state.isSendingChat) return;

    state.chat.push({ isUser: true, text: question });
    renderChat();
    state.isSendingChat = true;
    $("btnCoachSend").disabled = true;
    const statusEl = $("coachStatus");
    statusEl.classList.remove("hidden");

    const cleanTurns = [];
    for (const msg of state.chat.slice(-14)) {
      if (!msg.isUser && (msg.text.startsWith("Could not reach Gemini:") || cleanTurns.length === 0)) continue;
      const role = msg.isUser ? "user" : "model";
      if (cleanTurns.length && cleanTurns[cleanTurns.length - 1].role === role) {
        cleanTurns[cleanTurns.length - 1].parts.push({ text: msg.text });
      } else {
        cleanTurns.push({ role, parts: [{ text: msg.text }] });
      }
    }

    const payload = {
      system_instruction: { parts: [{ text: buildCoachContext() }] },
      contents: cleanTurns,
      generationConfig: { temperature: 0.5, maxOutputTokens: 2048 }
    };

    const primary = (state.prefs.geminiModel || DEFAULT_GEMINI_MODEL).trim();
    const candidates = [primary, "gemini-flash-latest", "gemini-flash-lite-latest"].filter((v, i, a) => a.indexOf(v) === i);

    try {
      let answer = null;
      let usedModel = primary;
      let lastErr = null;
      for (let i = 0; i < candidates.length; i++) {
        const m = candidates[i];
        statusEl.textContent = i === 0 ? `Thinking with ${m}...` : `${primary} busy — trying ${m}...`;
        try {
          answer = await callGeminiOnce(key, m, payload);
          usedModel = m;
          break;
        } catch (e) {
          lastErr = e;
          if (!e.retryable) throw e;
        }
      }
      if (!answer) throw lastErr || new Error("No Gemini model was available.");
      const finalReply = usedModel === primary ? answer : `${answer}\n\n(Answered via ${usedModel} while ${primary} was busy.)`;
      state.chat.push({ isUser: false, text: finalReply });
    } catch (e) {
      state.chat.push({ isUser: false, text: `Could not reach Gemini: ${e.message}` });
    } finally {
      state.isSendingChat = false;
      $("btnCoachSend").disabled = false;
      statusEl.classList.add("hidden");
      renderChat();
    }
  }

  // ---------- Rendering ----------
  function renderProgress() {
    const today = todayYmd();
    const d7 = daysAgoYmd(6);
    let wkRuns = 0, wkSec = 0, wkMeters = 0;
    for (const r of state.runs) {
      if (r.date >= d7 && r.date <= today) { wkRuns++; wkSec += r.movingTime; wkMeters += r.distance; }
    }
    $("wkRuns").textContent = wkRuns;
    $("wkTime").textContent = fmtDuration(wkSec);
    $("wkDistance").textContent = fmtDistance(wkMeters);

    if (state.prefs.intervalsKey) {
      const who = state.prefs.athleteName || "Garmin Sync Ready";
      $("syncStatusLine").textContent = `${who} • ${state.runs.length} runs • Synced ${fmtTimestamp(state.prefs.lastSyncMs)}`;
      $("syncSubtitle").textContent = "Click Sync anytime to fetch your newest Garmin runs & wellness.";
      $("btnDashSync").textContent = "Sync";
    } else {
      $("syncStatusLine").textContent = state.runs.length ? `${state.runs.length} runs stored locally` : "Not connected yet";
      $("syncSubtitle").textContent = "Connect free Garmin sync via Intervals.icu in You → Settings.";
      $("btnDashSync").textContent = "Connect";
    }

    const thisYear = String(new Date().getFullYear());
    const d30 = daysAgoYmd(30);
    const filtered = state.period === "all"
      ? state.runs
      : state.runs.filter((r) => (state.period === "year" ? r.date.startsWith(thisYear) : r.date >= d30));

    const sum = computeSummary(filtered);
    $("statDistance").textContent = fmtDistance(sum.dist);
    $("statRuns").textContent = sum.count;
    $("statPace").textContent = fmtPace(sum.avgPaceSec);
    $("statHrElev").textContent = `${sum.hrCount ? fmtHr(sum.avgHr) : "-- bpm"}\n+${fmtElevation(sum.elev)}`;

    // Wellness
    const ws = computeWellnessSummary(state.wellness);
    $("healthVo2").textContent = ws.vo2 ? ws.vo2.toFixed(1) : "--";
    $("healthRhr").textContent = ws.rhr ? `${Math.round(ws.rhr)} bpm${ws.avg30Rhr ? ` (30d: ${Math.round(ws.avg30Rhr)})` : ""}` : "--";
    $("healthHrv").textContent = ws.hrv ? `${Math.round(ws.hrv)} ms${ws.avg30Hrv ? ` (30d: ${Math.round(ws.avg30Hrv)})` : ""}` : "--";
    if (ws.sleepScore || ws.avg30SleepHrs) {
      const parts = [];
      if (ws.avg30SleepHrs) parts.push(`${ws.avg30SleepHrs.toFixed(1)}h`);
      if (ws.sleepScore) parts.push(`Score ${Math.round(ws.sleepScore)}`);
      $("healthSleep").textContent = parts.join(" • ");
    } else {
      $("healthSleep").textContent = "--";
    }

    // Personal Bests
    const prs = computePersonalBests(state.runs);
    const prEl = $("prList");
    if (!prs.length) {
      prEl.textContent = "Sync runs of 5K or longer to see your all-time Personal Bests.";
    } else {
      const f = state.prefs.useMiles ? 1.609344 : 1;
      prEl.innerHTML = prs
        .map((p) => `<div><strong>${p.label}:</strong> ${fmtDuration(p.estSec)} (${fmtPace(p.paceSecPerKm * f)}) — ${fmtDate(p.run.date)}</div>`)
        .join("");
    }

    // Load & Insights
    const loadEl = $("loadSummary");
    if (!state.runs.length) {
      loadEl.textContent = "Sync runs or click Load Sample to see volume ramp, streaks, and aerobic efficiency.";
    } else {
      const ins = computeInsights(state.runs);
      const ramp = ins.acwr <= 0.01 ? "Building baseline"
        : ins.acwr < 0.8 ? "Recovery / light week"
        : ins.acwr <= 1.3 ? "Optimal build zone"
        : ins.acwr <= 1.5 ? "High ramp — watch fatigue"
        : "Very sharp spike — consider extra rest";
      const rows = [
        `• <strong>Last 7 days:</strong> ${fmtDistance(ins.last7)} (28d weekly avg: ${fmtDistance(ins.wkAvg28)})`,
        `• <strong>Load ratio (ACWR):</strong> ${ins.acwr.toFixed(2)} — ${ramp}`,
        `• <strong>Weekly streak:</strong> ${ins.currentStreak} weeks current (${ins.longestStreak} longest)`
      ];
      if (ins.recEff > 0) {
        const delta = ins.oldEff > 0 ? ` (${ins.effDeltaPct >= 0 ? "+" : ""}${ins.effDeltaPct.toFixed(1)}% vs prior 90d)` : "";
        rows.push(`• <strong>Aerobic efficiency (90d):</strong> ${ins.recEff.toFixed(2)} m/beat${delta}`);
      }
      loadEl.innerHTML = rows.join("<br />");
    }

    // Period Highlights
    const hiEl = $("periodHighlights");
    if (!filtered.length) {
      hiEl.textContent = "No runs recorded in this period yet.";
    } else {
      const rows = [`• <strong>Total moving time:</strong> ${fmtDuration(sum.moving)}`];
      if (sum.longest) rows.push(`• <strong>Longest run:</strong> ${fmtDistance(sum.longest.distance)} (${fmtDate(sum.longest.date)})`);
      if (sum.fastest) rows.push(`• <strong>Fastest 5K+ run:</strong> ${fmtPace(runPaceSec(sum.fastest))} over ${fmtDistance(sum.fastest.distance)} (${fmtDate(sum.fastest.date)})`);
      if (sum.climb && sum.climb.elevation > 0) rows.push(`• <strong>Biggest climb:</strong> +${fmtElevation(sum.climb.elevation)} (${escapeHtml(sum.climb.name)})`);
      hiEl.innerHTML = rows.join("<br />");
    }
  }

  function cssVar(name) {
    return getComputedStyle(document.body).getPropertyValue(name).trim();
  }

  function mountChart(key, canvasId, config) {
    if (state.charts[key]) {
      state.charts[key].destroy();
    }
    const ctx = $(canvasId).getContext("2d");
    state.charts[key] = new Chart(ctx, config);
  }

  function renderCharts() {
    const empty = $("chartsEmpty");
    const u = unitLabel();
    const rangeLabel = state.chartMonths === 12 ? "12 Months" : state.chartMonths === 24 ? "2 Years" : "All Years";
    $("titlePaceHr").textContent = `Heart Rate vs. Pace (${rangeLabel})`;
    $("titleYearly").textContent = `Yearly Distance (${u})`;
    $("titleCumulative").textContent = `Cumulative Year Comparison (${u})`;
    $("titleMonthly").textContent = `Monthly Distance — ${rangeLabel} (${u})`;

    if (!state.runs.length) {
      empty.classList.remove("hidden");
      Object.values(state.charts).forEach((c) => c.destroy());
      state.charts = {};
      $("hrZoneTable").innerHTML = `<p class="muted">Sync runs with heart rate data to compare your pace in each HR zone.</p>`;
      return;
    }
    empty.classList.add("hidden");

    const blue = cssVar("--blue");
    const cyan = cssVar("--cyan");
    const indigo = cssVar("--indigo");
    const slate = cssVar("--slate");
    const textSec = cssVar("--text-secondary");
    const gridCol = cssVar("--divider");

    const monthly = computeMonthlyRuns(state.runs, state.chartMonths);
    const labels = monthly.map((m) => m.label);

    // 1. Heart Rate vs Pace Over Time
    const paceData = monthly.map((m) => {
      const dist = distInUnit(m.dist);
      if (dist <= 0.01 || m.moving <= 0) return null;
      const pMin = (m.moving / dist) / 60;
      return pMin > 1 && pMin < 20 ? +pMin.toFixed(2) : null;
    });
    const hrData = monthly.map((m) => (m.hrSec > 0 ? +(m.hrSum / m.hrSec).toFixed(1) : null));

    mountChart("paceHr", "chartPaceHr", {
      type: "line",
      data: {
        labels,
        datasets: [
          {
            label: `Avg Pace (min/${u}, faster ↑)`,
            data: paceData,
            yAxisID: "yPace",
            borderColor: blue,
            backgroundColor: blue,
            borderWidth: 2.6,
            tension: 0.3,
            spanGaps: true,
            pointRadius: monthly.length > 24 ? 2 : 3.5
          },
          {
            label: "Avg HR (bpm, easier ↓)",
            data: hrData,
            yAxisID: "yHr",
            borderColor: indigo,
            backgroundColor: indigo,
            borderWidth: 2.2,
            tension: 0.3,
            spanGaps: true,
            pointRadius: monthly.length > 24 ? 2 : 3
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: "index", intersect: false },
        plugins: {
          legend: { labels: { color: textSec } },
          tooltip: {
            callbacks: {
              label: (ctx) => ctx.dataset.yAxisID === "yPace"
                ? `Avg Pace: ${fmtPaceShort(ctx.parsed.y)} /${u}`
                : `Avg HR: ${Math.round(ctx.parsed.y)} bpm`
            }
          }
        },
        scales: {
          x: { ticks: { color: textSec, maxTicksLimit: 9 }, grid: { display: false } },
          yPace: {
            type: "linear",
            position: "left",
            reverse: true,
            ticks: { color: blue, callback: (v) => fmtPaceShort(v) },
            grid: { color: gridCol }
          },
          yHr: {
            type: "linear",
            position: "right",
            ticks: { color: indigo },
            grid: { drawOnChartArea: false }
          }
        }
      }
    });

    // 2. Aerobic Efficiency
    const ins = computeInsights(state.runs);
    if (ins.recEff > 0) {
      $("efficiencySummary").textContent = ins.oldEff > 0
        ? `Last 90d: ${ins.recEff.toFixed(2)} m/beat (${ins.effDeltaPct >= 0 ? "+" : ""}${ins.effDeltaPct.toFixed(1)}% vs prior 90d) — higher is fitter`
        : `Last 90d: ${ins.recEff.toFixed(2)} meters per heartbeat — higher is fitter`;
    } else {
      $("efficiencySummary").textContent = "Needs runs with heart rate data to compute aerobic efficiency.";
    }

    const effData = monthly.map((m) => (m.effSec > 0 ? +(m.effSum / m.effSec).toFixed(3) : null));
    mountChart("eff", "chartEfficiency", {
      type: "line",
      data: {
        labels,
        datasets: [{
          label: "Meters / Heartbeat",
          data: effData,
          borderColor: cyan,
          backgroundColor: "rgba(0, 163, 191, 0.14)",
          fill: true,
          borderWidth: 2.5,
          tension: 0.3,
          spanGaps: true,
          pointRadius: monthly.length > 24 ? 2 : 3.5
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { color: textSec, maxTicksLimit: 8 }, grid: { display: false } },
          y: { ticks: { color: textSec, callback: (v) => Number(v).toFixed(2) }, grid: { color: gridCol } }
        }
      }
    });

    // 3. Pace by HR Zone
    const zones = computeHrZoneComparison(state.runs);
    const zEl = $("hrZoneTable");
    if (!zones.length) {
      zEl.innerHTML = `<p class="muted">Sync runs with heart rate data to compare your pace in each HR zone.</p>`;
    } else {
      const f = state.prefs.useMiles ? 1.609344 : 1;
      zEl.innerHTML = zones.map((z) => {
        let paces = "";
        let badge = "";
        if (z.recentN > 0 && z.olderN > 0) {
          const rec = z.recentPaceKm * f;
          const old = z.olderPaceKm * f;
          const diff = Math.round(rec - old);
          paces = `${fmtPace(rec)} <span class="muted">vs ${fmtPace(old)}</span>`;
          if (diff <= -2) badge = `<span class="badge badge-good">${Math.abs(diff)}s /${u} faster</span>`;
          else if (diff >= 2) badge = `<span class="badge badge-warn">+${diff}s /${u}</span>`;
          else badge = `<span class="badge badge-neutral">steady</span>`;
        } else if (z.recentN > 0) {
          paces = `${fmtPace(z.recentPaceKm * f)} (${z.recentN} recent)`;
        } else {
          paces = `${fmtPace(z.olderPaceKm * f)} (${z.olderN} prior)`;
        }
        return `<div class="zone-row"><div><div class="zone-name">${z.label}</div><div class="zone-paces">${paces}</div></div>${badge}</div>`;
      }).join("");
    }

    // 4. Wellness Chart
    const mWell = computeMonthlyWellness(state.wellness, state.chartMonths);
    const vo2Vals = mWell.map((m) => (m.vo2N ? +(m.vo2Sum / m.vo2N).toFixed(1) : null));
    const rhrVals = mWell.map((m) => (m.rhrN ? +(m.rhrSum / m.rhrN).toFixed(1) : null));
    const hasWell = vo2Vals.some((v) => v !== null) || rhrVals.some((v) => v !== null);
    $("wellnessChartNote").textContent = hasWell
      ? "Blue = VO2 Max (left) • Cyan = Resting HR bpm (right)"
      : "Sync with Intervals.icu (or load sample data) to see Garmin VO2 Max & Resting HR trends.";

    mountChart("wellness", "chartWellness", {
      type: "line",
      data: {
        labels: mWell.map((m) => m.label),
        datasets: [
          {
            label: "VO2 Max",
            data: vo2Vals,
            yAxisID: "yVo2",
            borderColor: blue,
            backgroundColor: blue,
            borderWidth: 2.5,
            tension: 0.3,
            spanGaps: true,
            pointRadius: 3
          },
          {
            label: "Resting HR (bpm)",
            data: rhrVals,
            yAxisID: "yRhr",
            borderColor: cyan,
            backgroundColor: cyan,
            borderWidth: 2.2,
            tension: 0.3,
            spanGaps: true,
            pointRadius: 3
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: "index", intersect: false },
        plugins: { legend: { labels: { color: textSec } } },
        scales: {
          x: { ticks: { color: textSec, maxTicksLimit: 9 }, grid: { display: false } },
          yVo2: { type: "linear", position: "left", ticks: { color: blue }, grid: { color: gridCol } },
          yRhr: { type: "linear", position: "right", ticks: { color: cyan }, grid: { drawOnChartArea: false } }
        }
      }
    });

    // 5. Yearly Distance
    const yearlyMap = new Map();
    for (const r of state.runs) {
      const y = r.date.slice(0, 4);
      yearlyMap.set(y, (yearlyMap.get(y) || 0) + distInUnit(r.distance));
    }
    const years = Array.from(yearlyMap.keys()).sort();
    mountChart("yearly", "chartYearly", {
      type: "bar",
      data: {
        labels: years,
        datasets: [{ label: `Distance (${u})`, data: years.map((y) => Math.round(yearlyMap.get(y))), backgroundColor: blue, borderRadius: 6 }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { color: textSec }, grid: { display: false } },
          y: { beginAtZero: true, ticks: { color: textSec }, grid: { color: gridCol } }
        }
      }
    });

    // 6. Cumulative Year Comparison (last 4 years)
    const monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    const recentYears = years.slice(-4);
    const palette = [slate, indigo, cyan, blue];
    const cumSets = recentYears.map((y, idx) => {
      const mBuckets = new Array(12).fill(0);
      for (const r of state.runs) {
        if (r.date.startsWith(y)) {
          const m = parseInt(r.date.slice(5, 7), 10) - 1;
          if (m >= 0 && m < 12) mBuckets[m] += distInUnit(r.distance);
        }
      }
      let acc = 0;
      const cum = mBuckets.map((v) => +(acc += v).toFixed(1));
      const color = palette[Math.min(palette.length - 1, Math.max(0, palette.length - recentYears.length) + idx)];
      return {
        label: y,
        data: cum,
        borderColor: color,
        backgroundColor: color,
        borderWidth: idx === recentYears.length - 1 ? 3 : 2,
        tension: 0.25,
        pointRadius: 2.5
      };
    });

    mountChart("cumulative", "chartCumulative", {
      type: "line",
      data: { labels: monthNames, datasets: cumSets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: "index", intersect: false },
        plugins: { legend: { labels: { color: textSec } } },
        scales: {
          x: { ticks: { color: textSec }, grid: { display: false } },
          y: { beginAtZero: true, ticks: { color: textSec }, grid: { color: gridCol } }
        }
      }
    });

    // 7. Monthly Distance
    mountChart("monthly", "chartMonthly", {
      type: "bar",
      data: {
        labels,
        datasets: [{ label: `Distance (${u})`, data: monthly.map((m) => +distInUnit(m.dist).toFixed(1)), backgroundColor: cyan, borderRadius: 4 }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { color: textSec, maxTicksLimit: 10 }, grid: { display: false } },
          y: { beginAtZero: true, ticks: { color: textSec }, grid: { color: gridCol } }
        }
      }
    });
  }

  function renderFeed() {
    const years = Array.from(new Set(state.runs.map((r) => r.date.slice(0, 4)))).sort().reverse();
    const chipContainer = $("feedYearChips");
    chipContainer.innerHTML = [`<button class="chip ${state.feedYear === "all" ? "active" : ""}" data-year="all">All Years</button>`]
      .concat(years.map((y) => `<button class="chip ${state.feedYear === y ? "active" : ""}" data-year="${y}">${y}</button>`))
      .join("");

    chipContainer.querySelectorAll(".chip").forEach((btn) => {
      btn.addEventListener("click", () => {
        state.feedYear = btn.dataset.year;
        renderFeed();
      });
    });

    const q = state.feedSearch.toLowerCase();
    const filtered = state.runs.filter((r) => {
      if (state.feedYear !== "all" && !r.date.startsWith(state.feedYear)) return false;
      if (q && !`${r.name} ${r.date} ${r.sportType}`.toLowerCase().includes(q)) return false;
      return true;
    });

    const totalM = filtered.reduce((a, r) => a + r.distance, 0);
    $("feedCount").textContent = `${filtered.length} activities • ${fmtDistance(totalM)}`;

    const athlete = state.prefs.athleteName || "Stravo Runner";
    const init = initialsOf(athlete);
    const listEl = $("feedList");

    if (!filtered.length) {
      listEl.innerHTML = `<div class="card empty-banner">No matching activities found.</div>`;
      return;
    }

    listEl.innerHTML = filtered.slice(0, 200).map((r) => {
      const meta = [`Elev Gain: +${fmtElevation(r.elevation)}`];
      if (r.avgHr > 40) meta.push(`Avg HR: ${fmtHr(r.avgHr)}`);
      if (r.load > 0) meta.push(`Load: ${Math.round(r.load)}`);
      return `
        <article class="run-card" data-run-id="${escapeHtml(r.id)}">
          <div class="run-header">
            <div class="avatar">${escapeHtml(init)}</div>
            <div>
              <div class="run-athlete">${escapeHtml(athlete)}</div>
              <div class="run-date">${fmtDate(r.date)} &bull; ${escapeHtml(r.sportType)}</div>
            </div>
          </div>
          <div class="run-title">${escapeHtml(r.name)}</div>
          <div class="run-metrics">
            <div class="run-metric">
              <span class="stat-label">Distance</span>
              <span class="run-metric-val accent">${fmtDistance(r.distance)}</span>
            </div>
            <div class="run-metric">
              <span class="stat-label">Pace</span>
              <span class="run-metric-val">${fmtPace(runPaceSec(r))}</span>
            </div>
            <div class="run-metric">
              <span class="stat-label">Time</span>
              <span class="run-metric-val">${fmtDuration(r.movingTime)}</span>
            </div>
          </div>
          <div class="run-meta">${meta.join(" &bull; ")}</div>
        </article>`;
    }).join("");

    listEl.querySelectorAll(".run-card").forEach((card) => {
      card.addEventListener("click", () => {
        const run = state.runs.find((x) => String(x.id) === card.dataset.runId);
        if (run) openRunModal(run);
      });
    });
  }

  function openRunModal(run) {
    $("modalTitle").textContent = run.name;
    const spm = run.avgCadence > 0 ? (run.avgCadence < 120 ? run.avgCadence * 2 : run.avgCadence) : 0;
    const eff = runEfficiency(run);
    const items = [
      ["Date", fmtDate(run.date)],
      ["Sport", run.sportType],
      ["Distance", fmtDistance(run.distance)],
      ["Avg Pace", fmtPace(runPaceSec(run))],
      ["Moving Time", fmtDuration(run.movingTime)],
      ["Elapsed Time", fmtDuration(run.elapsedTime)],
      ["Elevation Gain", `+${fmtElevation(run.elevation)}`],
      ["Avg Heart Rate", fmtHr(run.avgHr)],
      ["Max Heart Rate", fmtHr(run.maxHr)],
      ["Cadence", spm > 0 ? `${Math.round(spm)} spm` : "--"],
      ["Aerobic Efficiency", eff > 0 ? `${eff.toFixed(2)} m/beat` : "--"],
      ["Training Load", run.load > 0 ? `${Math.round(run.load)}` : "--"]
    ];
    $("modalBody").innerHTML = items.map(([k, v]) => `<div class="modal-kv"><span>${k}</span><strong>${escapeHtml(String(v))}</strong></div>`).join("");
    $("runModal").classList.remove("hidden");
  }

  function renderChat() {
    if (!state.chat.length) {
      const msg = state.prefs.geminiKey
        ? `Hi! I have ${state.runs.length} of your runs loaded. Tap a prompt above or ask about your HR vs. pace trends, VO2 Max, mileage ramp, or next week's plan!`
        : `Hi! I'm your AI Running Coach (${state.runs.length} runs loaded).\n\nPaste your personal Gemini API key in You → Settings to start chatting with your running stats.`;
      state.chat.push({ isUser: false, text: msg });
    }
    const container = $("chatMessages");
    container.innerHTML = state.chat.map((m) => `<div class="bubble ${m.isUser ? "user" : "coach"}">${escapeHtml(m.text)}</div>`).join("");
    container.scrollTop = container.scrollHeight;
  }

  function renderSettings() {
    $("toggleMiles").checked = state.prefs.useMiles;
    $("toggleDark").checked = state.prefs.darkMode;
    $("inputIntervalsKey").value = state.prefs.intervalsKey;
    $("inputGeminiKey").value = state.prefs.geminiKey;
    $("inputGeminiModel").value = state.prefs.geminiModel || DEFAULT_GEMINI_MODEL;

    if (state.prefs.intervalsKey) {
      const who = state.prefs.athleteName ? `Connected as ${state.prefs.athleteName}` : "API Key Saved";
      $("settingsConnStatus").textContent = `Status: ${who} • ${state.runs.length} runs (Last sync: ${fmtTimestamp(state.prefs.lastSyncMs)})`;
    } else {
      $("settingsConnStatus").textContent = `Status: Not connected • ${state.runs.length} runs stored locally`;
    }
  }

  function applyTheme() {
    document.body.classList.toggle("dark", !!state.prefs.darkMode);
  }

  function renderAll() {
    applyTheme();
    renderProgress();
    renderCharts();
    renderFeed();
    renderChat();
    renderSettings();
  }

  function switchTab(tabName) {
    document.querySelectorAll(".tab-panel").forEach((p) => p.classList.remove("active"));
    document.querySelectorAll(".nav-btn, .mnav-btn").forEach((b) => {
      b.classList.toggle("active", b.dataset.tab === tabName);
    });
    const panel = $(`tab-${tabName}`);
    if (panel) panel.classList.add("active");
    if (tabName === "charts") renderCharts();
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  // ---------- Event Wiring ----------
  function initEvents() {
    document.querySelectorAll(".nav-btn, .mnav-btn").forEach((b) => {
      b.addEventListener("click", () => switchTab(b.dataset.tab));
    });

    $("btnThemeToggle").addEventListener("click", () => {
      state.prefs.darkMode = !state.prefs.darkMode;
      savePrefs();
      renderAll();
    });

    const quickSync = () => {
      if (!state.prefs.intervalsKey) {
        switchTab("settings");
        toast("Add your free Intervals.icu API key to sync Garmin runs.");
      } else {
        syncIntervals(false, (msg) => { $("syncSubtitle").textContent = msg; });
      }
    };
    $("btnTopSync").addEventListener("click", quickSync);
    $("btnDashSync").addEventListener("click", quickSync);
    $("btnDashSample").addEventListener("click", seedSampleData);
    $("btnSettingsSample").addEventListener("click", seedSampleData);

    $("periodChips").querySelectorAll(".chip").forEach((btn) => {
      btn.addEventListener("click", () => {
        $("periodChips").querySelectorAll(".chip").forEach((c) => c.classList.remove("active"));
        btn.classList.add("active");
        state.period = btn.dataset.period;
        renderProgress();
      });
    });

    $("chartRangeChips").querySelectorAll(".chip").forEach((btn) => {
      btn.addEventListener("click", () => {
        $("chartRangeChips").querySelectorAll(".chip").forEach((c) => c.classList.remove("active"));
        btn.classList.add("active");
        state.chartMonths = parseInt(btn.dataset.months, 10);
        renderCharts();
      });
    });

    $("feedSearch").addEventListener("input", (e) => {
      state.feedSearch = e.target.value.trim();
      renderFeed();
    });

    document.querySelectorAll(".prompt-chip").forEach((btn) => {
      btn.addEventListener("click", () => askGeminiCoach(btn.dataset.prompt));
    });

    $("coachForm").addEventListener("submit", (e) => {
      e.preventDefault();
      const q = $("coachInput").value.trim();
      if (!q) return;
      $("coachInput").value = "";
      askGeminiCoach(q);
    });

    $("toggleMiles").addEventListener("change", (e) => {
      state.prefs.useMiles = e.target.checked;
      savePrefs();
      renderAll();
    });

    $("toggleDark").addEventListener("change", (e) => {
      state.prefs.darkMode = e.target.checked;
      savePrefs();
      renderAll();
    });

    const showProg = (msg) => {
      const el = $("settingsSyncProgress");
      el.classList.remove("hidden");
      el.textContent = msg;
    };

    $("btnSaveFullSync").addEventListener("click", () => {
      state.prefs.intervalsKey = $("inputIntervalsKey").value.trim();
      savePrefs();
      syncIntervals(true, showProg);
    });

    $("btnSyncNew").addEventListener("click", () => {
      const typed = $("inputIntervalsKey").value.trim();
      if (typed) { state.prefs.intervalsKey = typed; savePrefs(); }
      syncIntervals(false, showProg);
    });

    $("btnSaveAi").addEventListener("click", () => {
      state.prefs.geminiKey = $("inputGeminiKey").value.trim();
      state.prefs.geminiModel = $("inputGeminiModel").value.trim() || DEFAULT_GEMINI_MODEL;
      savePrefs();
      toast("AI Coach settings saved!");
      if (state.chat.length === 1 && !state.chat[0].isUser) {
        state.chat = [];
        renderChat();
      }
    });

    const bindCsv = (inputId, inMiles) => {
      $(inputId).addEventListener("change", async (e) => {
        const file = e.target.files?.[0];
        if (!file) return;
        const st = $("csvStatus");
        st.classList.remove("hidden");
        st.textContent = "Importing Garmin CSV...";
        try {
          const n = await importGarminCsv(file, inMiles);
          st.textContent = n > 0 ? `Imported ${n} runs from ${file.name}!` : "No running activities found in that CSV.";
          toast(st.textContent);
        } catch (err) {
          st.textContent = `Import failed: ${err.message}`;
          toast(st.textContent);
        }
        e.target.value = "";
      });
    };
    bindCsv("csvFileKm", false);
    bindCsv("csvFileMi", true);

    $("btnClearRuns").addEventListener("click", () => {
      if (!confirm("Clear all cached runs & health entries from this browser?")) return;
      state.runs = [];
      state.wellness = [];
      saveData();
      renderAll();
      toast("Local data cleared.");
    });

    $("btnRemoveKey").addEventListener("click", () => {
      state.prefs.intervalsKey = "";
      state.prefs.athleteName = "";
      savePrefs();
      renderAll();
      toast("Removed Intervals.icu API key.");
    });

    $("btnCloseModal").addEventListener("click", () => $("runModal").classList.add("hidden"));
    $("runModal").querySelector(".modal-backdrop").addEventListener("click", () => $("runModal").classList.add("hidden"));
  }

  loadState();
  initEvents();
  renderAll();
})();

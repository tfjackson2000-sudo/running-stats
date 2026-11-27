package com.runningstats.app;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.runningstats.app.data.GarminCsvImporter;
import com.runningstats.app.data.IntervalsClient;
import com.runningstats.app.data.PrefsManager;
import com.runningstats.app.data.RunDatabaseHelper;
import com.runningstats.app.ui.CoachFragment;
import com.runningstats.app.ui.DashboardFragment;
import com.runningstats.app.ui.GraphsFragment;
import com.runningstats.app.ui.RefreshableFragment;
import com.runningstats.app.ui.RunsFragment;
import com.runningstats.app.ui.SettingsFragment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public interface SyncCallback {
        void onProgress(String message);
        void onComplete(boolean success, String message);
    }

    private PrefsManager prefs;
    private RunDatabaseHelper db;
    private ExecutorService executor;
    private Handler mainHandler;

    private static final long AUTO_SYNC_INTERVAL_MS = 30L * 60L * 1000L; // 30 minutes

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private boolean isSyncing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);
        db = new RunDatabaseHelper(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        toolbar = findViewById(R.id.topToolbar);
        bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                showFragment(new DashboardFragment(), "STRAVO");
                return true;
            } else if (id == R.id.nav_graphs) {
                showFragment(new GraphsFragment(), "Charts");
                return true;
            } else if (id == R.id.nav_runs) {
                showFragment(new RunsFragment(), "Feed");
                return true;
            } else if (id == R.id.nav_coach) {
                showFragment(new CoachFragment(), "AI Coach");
                return true;
            } else if (id == R.id.nav_settings) {
                showFragment(new SettingsFragment(), "You & Settings");
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeAutoSync();
    }

    private void maybeAutoSync() {
        if (isSyncing || !prefs.isIntervalsConnected()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - prefs.getLastSyncMs();
        if (elapsed >= AUTO_SYNC_INTERVAL_MS) {
            // Silently sync latest runs & Garmin wellness when opening the app
            syncIntervals(false, new SyncCallback() {
                @Override
                public void onProgress(String message) {
                    // Quiet background sync
                }

                @Override
                public void onComplete(boolean success, String message) {
                    if (success) {
                        notifyDataChanged();
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void showFragment(Fragment fragment, String title) {
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    public PrefsManager getPrefs() {
        return prefs;
    }

    public RunDatabaseHelper getDb() {
        return db;
    }

    public void navigateToTab(int menuItemId) {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(menuItemId);
        }
    }

    public void notifyDataChanged() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (current instanceof RefreshableFragment) {
            ((RefreshableFragment) current).refreshData();
        }
    }

    public void syncIntervals(boolean fullHistory, SyncCallback callback) {
        if (isSyncing) {
            if (callback != null) {
                callback.onComplete(false, "Sync already in progress...");
            }
            return;
        }
        if (!prefs.isIntervalsConnected()) {
            if (callback != null) {
                callback.onComplete(false, "Add your free Intervals.icu API key in Settings first.");
            } else {
                Toast.makeText(this, "Add your free Intervals.icu API key in Settings first.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        isSyncing = true;
        executor.execute(() -> {
            try {
                if (callback != null) {
                    mainHandler.post(() -> callback.onProgress("Connecting to Intervals.icu..."));
                }
                IntervalsClient.verifyAndFetchAthleteName(prefs);
                int synced = IntervalsClient.syncRuns(prefs, db, fullHistory, msg ->
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onProgress(msg);
                            }
                        })
                );
                int wellnessSynced = 0;
                try {
                    wellnessSynced = IntervalsClient.syncWellness(prefs, db, fullHistory, msg ->
                            mainHandler.post(() -> {
                                if (callback != null) {
                                    callback.onProgress(msg);
                                }
                            })
                    );
                } catch (Exception ignored) {
                }
                String doneMsg = "Synced " + synced + " run" + (synced == 1 ? "" : "s")
                        + (wellnessSynced > 0 ? " & " + wellnessSynced + " health days." : " from Intervals.icu.");
                mainHandler.post(() -> {
                    isSyncing = false;
                    notifyDataChanged();
                    if (callback != null) {
                        callback.onComplete(true, doneMsg);
                    } else {
                        Toast.makeText(MainActivity.this, doneMsg, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                String err = "Sync failed: " + e.getMessage();
                mainHandler.post(() -> {
                    isSyncing = false;
                    if (callback != null) {
                        callback.onComplete(false, err);
                    } else {
                        Toast.makeText(MainActivity.this, err, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    public void importGarminCsv(Uri csvUri, boolean csvInMiles, SyncCallback callback) {
        if (isSyncing) {
            if (callback != null) {
                callback.onComplete(false, "Another sync/import is already running...");
            }
            return;
        }
        isSyncing = true;
        executor.execute(() -> {
            try {
                if (callback != null) {
                    mainHandler.post(() -> callback.onProgress("Reading Garmin CSV..."));
                }
                int imported = GarminCsvImporter.importFromUri(MainActivity.this, csvUri, db, csvInMiles);
                prefs.setLastSyncMs(System.currentTimeMillis());
                String doneMsg = imported > 0
                        ? "Imported " + imported + " runs from Garmin CSV!"
                        : "No running activities found in that CSV file.";
                mainHandler.post(() -> {
                    isSyncing = false;
                    notifyDataChanged();
                    if (callback != null) {
                        callback.onComplete(imported > 0, doneMsg);
                    } else {
                        Toast.makeText(MainActivity.this, doneMsg, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                String err = "CSV import failed: " + e.getMessage();
                mainHandler.post(() -> {
                    isSyncing = false;
                    if (callback != null) {
                        callback.onComplete(false, err);
                    } else {
                        Toast.makeText(MainActivity.this, err, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}

package com.runningstats.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.runningstats.app.data.PrefsManager;
import com.runningstats.app.data.RunDatabaseHelper;
import com.runningstats.app.data.StravaClient;
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
                showFragment(new DashboardFragment(), "RunStats");
                return true;
            } else if (id == R.id.nav_graphs) {
                showFragment(new GraphsFragment(), "Graphs & Trends");
                return true;
            } else if (id == R.id.nav_runs) {
                showFragment(new RunsFragment(), "Run History");
                return true;
            } else if (id == R.id.nav_coach) {
                showFragment(new CoachFragment(), "AI Running Coach");
                return true;
            } else if (id == R.id.nav_settings) {
                showFragment(new SettingsFragment(), "Settings");
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }

        handleOAuthIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOAuthIntent(intent);
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

    private void handleOAuthIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri data = intent.getData();
        if (data == null) {
            return;
        }
        if ("runningstats".equalsIgnoreCase(data.getScheme())) {
            String error = data.getQueryParameter("error");
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, "Strava authorization canceled: " + error, Toast.LENGTH_LONG).show();
                return;
            }
            String code = data.getQueryParameter("code");
            if (code != null && !code.isEmpty()) {
                // Clear intent data so rotation won't replay the one-time OAuth code
                intent.setData(null);
                exchangeStravaCode(code);
            }
        }
    }

    public void exchangeStravaCode(String rawInput) {
        String code = StravaClient.extractCodeFromInput(rawInput);
        if (code.isEmpty()) {
            Toast.makeText(this, "No authorization code found.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Connecting to Strava...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                String athleteName = StravaClient.exchangeCodeForTokens(prefs, code);
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Connected as " + athleteName + "! Starting sync...", Toast.LENGTH_LONG).show();
                    notifyDataChanged();
                    syncStrava(true, null);
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(
                        MainActivity.this,
                        "Strava connect failed: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    public void syncStrava(boolean fullHistory, SyncCallback callback) {
        if (isSyncing) {
            if (callback != null) {
                callback.onComplete(false, "Sync already in progress...");
            }
            return;
        }
        if (!prefs.isStravaConnected()) {
            if (callback != null) {
                callback.onComplete(false, "Connect Strava in Settings first.");
            } else {
                Toast.makeText(this, "Connect Strava in Settings first.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        isSyncing = true;
        executor.execute(() -> {
            try {
                int synced = StravaClient.syncRuns(prefs, db, fullHistory, msg ->
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onProgress(msg);
                            }
                        })
                );
                String doneMsg = "Synced " + synced + " run" + (synced == 1 ? "" : "s") + " from Strava.";
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
}

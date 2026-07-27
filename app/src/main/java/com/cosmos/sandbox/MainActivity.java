package com.cosmos.sandbox;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class MainActivity extends Activity implements UniverseView.Listener {
    private static final String PREFS = "cosmos_save";
    private static final String SAVE_KEY = "world";
    private final SimulationEngine engine = new SimulationEngine();
    private UniverseView universeView;
    private TextView stats;
    private TextView selection;
    private Button pauseButton;
    private Button speedButton;
    private Button trailButton;
    private int speedIndex = 1;
    private final double[] speeds = {0.25, 1, 4, 12, 40};
    private int presetIndex;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        hideSystemUi();

        universeView = new UniverseView(this, engine);
        universeView.setListener(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(universeView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(buildTopPanel(), topPanelParams());
        root.addView(buildBottomPanel(), bottomPanelParams());
        setContentView(root);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!engine.deserialize(prefs.getString(SAVE_KEY, null))) engine.loadSolarSystem();
        universeView.resetCamera();
    }

    private View buildTopPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        panel.setBackgroundColor(Color.argb(190, 12, 17, 29));

        TextView title = new TextView(this);
        title.setText("COSMOS SANDBOX");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);

        stats = new TextView(this);
        stats.setTextColor(Color.rgb(175, 205, 230));
        stats.setTextSize(12);
        stats.setText("Loading simulation…");
        panel.addView(stats);

        selection = new TextView(this);
        selection.setTextColor(Color.rgb(107, 203, 255));
        selection.setTextSize(12);
        selection.setText("Tap a body to select it");
        panel.addView(selection);
        return panel;
    }

    private View buildBottomPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(8), dp(6), dp(8), dp(6));
        panel.setBackgroundColor(Color.argb(210, 12, 17, 29));

        pauseButton = button("Pause", v -> togglePause());
        speedButton = button("Speed 1×", v -> cycleSpeed());
        trailButton = button("Trails On", v -> toggleTrails());
        panel.addView(pauseButton);
        panel.addView(speedButton);
        panel.addView(button("Add planet", v -> addPlanet()));
        panel.addView(button("Add star", v -> addStar()));
        panel.addView(button("Delete", v -> deleteSelected()));
        panel.addView(trailButton);
        panel.addView(button("Preset", v -> cyclePreset()));
        panel.addView(button("Save", v -> saveWorld()));
        panel.addView(button("Load", v -> loadWorld()));
        panel.addView(button("Camera", v -> universeView.resetCamera()));
        return panel;
    }

    private Button button(String text, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(36, 52, 77)));
        b.setOnClickListener(action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(43));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void togglePause() {
        universeView.setPaused(!universeView.isPaused());
        pauseButton.setText(universeView.isPaused() ? "Play" : "Pause");
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % speeds.length;
        universeView.setTimeScale(speeds[speedIndex]);
        speedButton.setText(String.format(Locale.US, "Speed %s×", trim(speeds[speedIndex])));
    }

    private void toggleTrails() {
        universeView.setTrails(!universeView.hasTrails());
        trailButton.setText(universeView.hasTrails() ? "Trails On" : "Trails Off");
    }

    private void addPlanet() {
        double[] p = universeView.screenToWorld(universeView.getWidth() * .52f, universeView.getHeight() * .45f);
        double angle = System.nanoTime() % 6283 / 1000.0;
        Body body = engine.add("New planet", p[0], p[1], Math.cos(angle) * 4.5, Math.sin(angle) * 4.5,
                150, 15, Color.rgb(72, 175, 255), false);
        if (body == null) toast("Object limit reached"); else universeView.selectNewest();
    }

    private void addStar() {
        double[] p = universeView.screenToWorld(universeView.getWidth() * .55f, universeView.getHeight() * .42f);
        Body body = engine.add("New star", p[0], p[1], 0, 0, 52000, 45,
                Color.rgb(255, 170, 70), false);
        if (body == null) toast("Object limit reached"); else universeView.selectNewest();
    }

    private void deleteSelected() {
        long id = universeView.getSelectedId();
        if (id < 0) { toast("Select a body first"); return; }
        engine.remove(id);
        universeView.clearSelection();
    }

    private void cyclePreset() {
        presetIndex = (presetIndex + 1) % 3;
        if (presetIndex == 0) { engine.loadSolarSystem(); toast("Solar system loaded"); }
        else if (presetIndex == 1) { engine.loadBinaryStars(); toast("Binary stars loaded"); }
        else { engine.loadChaos(); toast("Orbital chaos loaded"); }
        universeView.clearSelection();
        universeView.resetCamera();
    }

    private void saveWorld() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(SAVE_KEY, engine.serialize()).apply();
        toast("Simulation saved");
    }

    private void loadWorld() {
        String data = getSharedPreferences(PREFS, MODE_PRIVATE).getString(SAVE_KEY, null);
        if (engine.deserialize(data)) {
            universeView.clearSelection();
            universeView.resetCamera();
            toast("Simulation loaded");
        } else toast("No valid save found");
    }

    @Override public void onSelectionChanged(Body body) {
        if (body == null) selection.setText("Tap a body to select it");
        else selection.setText(String.format(Locale.US, "%s  •  mass %.0f  •  radius %.1f  •  speed %.2f",
                body.name, body.mass, body.radius, Math.hypot(body.vx, body.vy)));
    }

    @Override public void onStatsChanged(String text) { stats.setText(text); }

    private FrameLayout.LayoutParams topPanelParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(285), -2);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setMargins(dp(12), dp(10), 0, 0);
        return lp;
    }

    private FrameLayout.LayoutParams bottomPanelParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(57));
        lp.gravity = Gravity.BOTTOM;
        return lp;
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private static String trim(double value) { return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value); }
}

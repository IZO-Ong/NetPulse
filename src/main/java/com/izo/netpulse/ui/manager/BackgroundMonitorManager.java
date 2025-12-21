package com.izo.netpulse.ui.manager;

import com.izo.netpulse.service.BackgroundMonitorService;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import java.util.prefs.Preferences;

public class BackgroundMonitorManager {

    private final BackgroundMonitorService service;
    private final CheckBox toggle;
    private final ComboBox<String> selector;
    private final Runnable onTestComplete;

    private static final String PREF_MONITOR_ENABLED = "monitor_enabled";
    private static final String PREF_MONITOR_INTERVAL = "monitor_interval";
    private final Preferences prefs = Preferences.userNodeForPackage(BackgroundMonitorManager.class);

    // MANUAL CONSTRUCTOR
    public BackgroundMonitorManager(
            BackgroundMonitorService service,
            CheckBox toggle,
            ComboBox<String> selector,
            Runnable onTestComplete) {

        this.service = service;
        this.toggle = toggle;
        this.selector = selector;
        this.onTestComplete = onTestComplete;

        // 1. Setup UI Items
        this.selector.getItems().addAll(
                "5 Min", "15 Min", "30 Min", "1 Hour", "3 Hours", "6 Hours"
        );

        // 2. Load and Auto-start if enabled
        loadSettings();
    }

    public void loadSettings() {
        boolean isEnabled = prefs.getBoolean(PREF_MONITOR_ENABLED, false);
        String interval = prefs.get(PREF_MONITOR_INTERVAL, "15 Min");

        toggle.setSelected(isEnabled);
        selector.setValue(interval);

        if (isEnabled) {
            applyMonitoring();
        }
    }

    public void handleSettingsChange() {
        prefs.putBoolean(PREF_MONITOR_ENABLED, toggle.isSelected());
        // Fix: Use .put() for Strings
        prefs.put(PREF_MONITOR_INTERVAL, selector.getValue());

        if (toggle.isSelected()) {
            applyMonitoring();
        } else {
            service.stopMonitoring();
        }
    }

    private void applyMonitoring() {
        int minutes = parseInterval(selector.getValue());
        service.startOrUpdateMonitoring(minutes, () -> Platform.runLater(onTestComplete));
    }

    private int parseInterval(String value) {
        if (value == null) return 15;
        return switch (value) {
            case "5 Mins"   -> 5;
            case "30 Mins"  -> 30;
            case "1 Hour"  -> 60;
            case "3 Hours" -> 180;
            case "6 Hours" -> 360;
            default        -> 15;
        };
    }
}
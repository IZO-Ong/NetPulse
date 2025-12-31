package com.izo.netpulse.ui.manager;

import com.izo.netpulse.service.BackgroundMonitorService;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import java.util.prefs.Preferences;

public class BackgroundMonitorManager {
    private final BackgroundMonitorService monitorService;
    private final CheckBox monitorToggle;
    private final ComboBox<String> intervalSelector;
    private final Runnable onTestComplete;

    private static final String PREF_MONITOR_ENABLED = "monitor_enabled";
    private static final String PREF_MONITOR_INTERVAL = "monitor_interval";
    private final Preferences prefs = Preferences.userNodeForPackage(BackgroundMonitorManager.class);

    public BackgroundMonitorManager(
            BackgroundMonitorService monitorService,
            CheckBox monitorToggle,
            ComboBox<String> intervalSelector,
            Runnable onTestComplete) {
        this.monitorService = monitorService;
        this.monitorToggle = monitorToggle;
        this.intervalSelector = intervalSelector;
        this.onTestComplete = onTestComplete;

        initUI();
    }

    private void initUI() {
        if (this.intervalSelector.getItems().isEmpty()) {
            this.intervalSelector.getItems().addAll(
                    "5 Min", "15 Min", "30 Min", "1 Hour", "3 Hours", "6 Hours"
            );
        }
    }

    public void loadSettings() {
        boolean monitorEnabled = prefs.getBoolean(PREF_MONITOR_ENABLED, false);
        String interval = prefs.get(PREF_MONITOR_INTERVAL, "15 Min");

        monitorToggle.setSelected(monitorEnabled);
        intervalSelector.setValue(interval);

        if (monitorEnabled) {
            applyMonitoring();
        }
    }

    public void handleMonitorSettingsChange() {
        prefs.putBoolean(PREF_MONITOR_ENABLED, monitorToggle.isSelected());
        prefs.put(PREF_MONITOR_INTERVAL, intervalSelector.getValue());

        if (monitorToggle.isSelected()) {
            applyMonitoring();
        } else {
            monitorService.stopMonitoring();
        }
    }

    private void applyMonitoring() {
        int minutes = parseInterval(intervalSelector.getValue());
        monitorService.startOrUpdateMonitoring(minutes, () -> Platform.runLater(onTestComplete));
    }

    private int parseInterval(String value) {
        return switch (value != null ? value : "15 Min") {
            case "5 Min"   -> 5;
            case "15 Min"  -> 15;
            case "30 Min"  -> 30;
            case "1 Hour"  -> 60;
            case "3 Hours" -> 180;
            case "6 Hours" -> 360;
            default        -> 15;
        };
    }
}
package com.izo.netpulse.ui.manager;

import com.izo.netpulse.service.BackgroundMonitorService;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import java.util.prefs.Preferences;

public class SettingsManager {

    private final BackgroundMonitorService monitorService;
    private final Parent rootContainer;

    // UI Controls
    private final CheckBox monitorToggle;
    private final ComboBox<String> intervalSelector;
    private final CheckBox lightModeToggle;
    private final Runnable onTestComplete;

    private static final String PREF_MONITOR_ENABLED = "monitor_enabled";
    private static final String PREF_MONITOR_INTERVAL = "monitor_interval";
    private static final String PREF_LIGHT_MODE = "light_mode_enabled";

    private final Preferences prefs = Preferences.userNodeForPackage(SettingsManager.class);

    public SettingsManager(
            BackgroundMonitorService monitorService,
            Parent rootContainer,
            CheckBox monitorToggle,
            ComboBox<String> intervalSelector,
            CheckBox lightModeToggle,
            Runnable onTestComplete) {

        this.monitorService = monitorService;
        this.rootContainer = rootContainer;
        this.monitorToggle = monitorToggle;
        this.intervalSelector = intervalSelector;
        this.lightModeToggle = lightModeToggle;
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
        // Load Monitoring State
        boolean monitorEnabled = prefs.getBoolean(PREF_MONITOR_ENABLED, false);
        String interval = prefs.get(PREF_MONITOR_INTERVAL, "15 Min");
        monitorToggle.setSelected(monitorEnabled);
        intervalSelector.setValue(interval);

        // Load Theme State
        boolean isLightMode = prefs.getBoolean(PREF_LIGHT_MODE, false);
        lightModeToggle.setSelected(isLightMode);

        // Apply visual states on startup
        applyTheme(isLightMode);
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

    public void handleThemeChange() {
        boolean isLightMode = lightModeToggle.isSelected();
        prefs.putBoolean(PREF_LIGHT_MODE, isLightMode);
        applyTheme(isLightMode);
    }

    private void applyTheme(boolean isLightMode) {
        if (isLightMode) {
            if (!rootContainer.getStyleClass().contains("light-mode")) {
                rootContainer.getStyleClass().add("light-mode");
            }
        } else {
            rootContainer.getStyleClass().remove("light-mode");
        }
    }

    private void applyMonitoring() {
        int minutes = parseInterval(intervalSelector.getValue());
        monitorService.startOrUpdateMonitoring(minutes, () -> Platform.runLater(onTestComplete));
    }

    private int parseInterval(String value) {
        if (value == null) return 15;
        return switch (value) {
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
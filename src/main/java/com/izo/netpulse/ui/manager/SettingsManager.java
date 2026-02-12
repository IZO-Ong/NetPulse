package com.izo.netpulse.ui.manager;

import com.izo.netpulse.service.BackgroundMonitorService;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import java.util.prefs.Preferences;

/**
 * Orchestrates the application's configuration and persistence layer.
 * This manager synchronizes user preferences between the JavaFX UI controls,
 * the CSS-based theming engine, and the background monitoring services.
 * Settings are persisted locally using the OS-specific Preferences node.
 */
public class SettingsManager {

    private final BackgroundMonitorService monitorService;
    private final Parent rootContainer;

    // UI Controls
    private final CheckBox monitorToggle;
    private final ComboBox<String> intervalSelector;
    private final CheckBox lightModeToggle;
    private final Runnable onTestComplete;

    // Preference Keys
    private static final String PREF_MONITOR_ENABLED = "monitor_enabled";
    private static final String PREF_MONITOR_INTERVAL = "monitor_interval";
    private static final String PREF_LIGHT_MODE = "light_mode_enabled";

    /** Persistent storage backed by the Java Preferences API. */
    private final Preferences prefs = Preferences.userNodeForPackage(SettingsManager.class);

    /**
     * Constructs a SettingsManager to coordinate UI state and service behavior.
     * @param monitorService The service responsible for scheduling background tests.
     * @param rootContainer  The root UI container (usually the main VBox) used for CSS theme switching.
     * @param monitorToggle  Checkbox to enable/disable periodic testing.
     * @param intervalSelector Dropdown for selecting the monitoring frequency.
     * @param lightModeToggle Checkbox for toggling the Light Mode theme.
     * @param onTestComplete Callback to refresh UI elements when a background test finishes.
     */
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

    /**
     * Populates the interval selector with standardized time intervals
     * if the control is not already initialized.
     */
    private void initUI() {
        if (this.intervalSelector.getItems().isEmpty()) {
            this.intervalSelector.getItems().addAll(
                    "5 Min", "15 Min", "30 Min", "1 Hour", "3 Hours", "6 Hours"
            );
        }
    }

    /**
     * Synchronizes the UI state with saved user preferences upon application startup.
     * This method applies the saved theme and initiates background monitoring if enabled.
     */
    public void loadSettings() {
        // Load Monitoring State
        boolean monitorEnabled = prefs.getBoolean(PREF_MONITOR_ENABLED, false);
        String interval = prefs.get(PREF_MONITOR_INTERVAL, "15 Min");
        monitorToggle.setSelected(monitorEnabled);
        intervalSelector.setValue(interval);

        // Load Theme State
        boolean isLightMode = prefs.getBoolean(PREF_LIGHT_MODE, false);
        lightModeToggle.setSelected(isLightMode);

        // Apply visual and service states on startup
        applyTheme(isLightMode);
        if (monitorEnabled) {
            applyMonitoring();
        }
    }

    /**
     * Persists updates to monitoring settings and signals the monitor service
     * to start, stop, or reschedule background tasks based on the new state.
     */
    public void handleMonitorSettingsChange() {
        prefs.putBoolean(PREF_MONITOR_ENABLED, monitorToggle.isSelected());
        prefs.put(PREF_MONITOR_INTERVAL, intervalSelector.getValue());

        if (monitorToggle.isSelected()) {
            applyMonitoring();
        } else {
            monitorService.stopMonitoring();
        }
    }

    /**
     * Persists the selected theme state and triggers a CSS style re-application
     * on the root container.
     */
    public void handleThemeChange() {
        boolean isLightMode = lightModeToggle.isSelected();
        prefs.putBoolean(PREF_LIGHT_MODE, isLightMode);
        applyTheme(isLightMode);
    }

    /**
     * Dynamically injects or removes the 'light-mode' CSS class on the root container.
     * This relies on a centralized stylesheet that defines alternate variables for
     * background and text colors.
     * @param isLightMode True if Light Mode should be active, False for Dark Mode.
     */
    private void applyTheme(boolean isLightMode) {
        if (isLightMode) {
            if (!rootContainer.getStyleClass().contains("light-mode")) {
                rootContainer.getStyleClass().add("light-mode");
            }
        } else {
            rootContainer.getStyleClass().remove("light-mode");
        }
    }

    /**
     * Parses the current UI interval selection and registers/updates the background
     * task in the monitor service. Refreshes the UI on the JavaFX thread upon completion.
     */
    private void applyMonitoring() {
        int minutes = parseInterval(intervalSelector.getValue());
        monitorService.startOrUpdateMonitoring(minutes, () -> Platform.runLater(onTestComplete));
    }

    /**
     * Converts human-readable string intervals into integer minute values.
     * @param value The string representation of the interval (e.g., "1 Hour").
     * @return The equivalent duration in minutes. Defaults to 15.
     */
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
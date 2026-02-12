package com.izo.netpulse.ui.manager;

import com.izo.netpulse.service.BackgroundMonitorService;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import java.util.prefs.Preferences;

/**
 * Manages the UI logic and persistent state for the Background Monitoring feature.
 * This class coordinates between the JavaFX settings controls and the
 * {@link BackgroundMonitorService}, ensuring that user preferences are
 * saved across application restarts using the Java Preferences API.
 */
public class BackgroundMonitorManager {
    private final BackgroundMonitorService monitorService;
    private final CheckBox monitorToggle;
    private final ComboBox<String> intervalSelector;
    private final Runnable onTestComplete;

    private static final String PREF_MONITOR_ENABLED = "monitor_enabled";
    private static final String PREF_MONITOR_INTERVAL = "monitor_interval";

    /** Persistent storage for user settings. */
    private final Preferences prefs = Preferences.userNodeForPackage(BackgroundMonitorManager.class);

    /**
     * Constructs a new BackgroundMonitorManager.
     * * @param monitorService The backend service that handles the scheduled task execution.
     * @param monitorToggle The UI checkbox used to enable or disable monitoring.
     * @param intervalSelector The UI dropdown used to select the polling frequency.
     * @param onTestComplete A callback to refresh UI components (e.g., charts) when a
     * background test concludes.
     */
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

    /**
     * Populates the interval selector with human-readable time options
     * if it is currently empty.
     */
    private void initUI() {
        if (this.intervalSelector.getItems().isEmpty()) {
            this.intervalSelector.getItems().addAll(
                    "5 Min", "15 Min", "30 Min", "1 Hour", "3 Hours", "6 Hours"
            );
        }
    }

    /**
     * Loads monitoring settings from the OS-specific persistent storage.
     * If monitoring was previously enabled, it automatically restarts the
     * background service using the saved interval.
     */
    public void loadSettings() {
        boolean monitorEnabled = prefs.getBoolean(PREF_MONITOR_ENABLED, false);
        String interval = prefs.get(PREF_MONITOR_INTERVAL, "15 Min");

        monitorToggle.setSelected(monitorEnabled);
        intervalSelector.setValue(interval);

        if (monitorEnabled) {
            applyMonitoring();
        }
    }

    /**
     * Processes changes in the monitoring UI controls.
     * Updates the persistent preferences and notifies the monitor service
     * to start, update, or stop the background task.
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
     * Parses the UI selection and initializes/updates the background monitoring task.
     * Wraps the {@code onTestComplete} callback in {@link Platform#runLater}
     * to ensure UI updates happen on the JavaFX Application Thread.
     */
    private void applyMonitoring() {
        int minutes = parseInterval(intervalSelector.getValue());
        monitorService.startOrUpdateMonitoring(minutes, () -> Platform.runLater(onTestComplete));
    }

    /**
     * Maps the human-readable ComboBox strings to integer minute values.
     * @param value The string value from the UI (e.g., "1 Hour").
     * @return The corresponding duration in minutes. Defaults to 15 if null or unrecognized.
     */
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
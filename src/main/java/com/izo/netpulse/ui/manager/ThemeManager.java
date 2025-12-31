package com.izo.netpulse.ui.manager;

import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import java.util.prefs.Preferences;

public class ThemeManager {
    private final Parent rootContainer;
    private final CheckBox lightModeToggle;
    private static final String PREF_LIGHT_MODE = "light_mode_enabled";
    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);

    public ThemeManager(Parent rootContainer, CheckBox lightModeToggle) {
        this.rootContainer = rootContainer;
        this.lightModeToggle = lightModeToggle;
    }

    public void loadSettings() {
        boolean isLightMode = prefs.getBoolean(PREF_LIGHT_MODE, false);
        lightModeToggle.setSelected(isLightMode);
        applyTheme(isLightMode);
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
}
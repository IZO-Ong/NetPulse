package com.izo.netpulse.ui.manager;

import javafx.animation.FadeTransition;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.util.Duration;
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

        FadeTransition fadeOut = new FadeTransition(Duration.millis(100), rootContainer);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(e -> {
            applyTheme(isLightMode);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(150), rootContainer);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });

        fadeOut.play();
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
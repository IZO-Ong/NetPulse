package com.izo.netpulse.ui.manager;

import javafx.animation.FadeTransition;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.util.Duration;
import java.util.prefs.Preferences;

/**
 * Manages the visual appearance of the application by toggling between Dark and Light modes.
 * This class handles the persistence of the user's theme preference and orchestrates
 * a smooth visual transition when the theme is changed at runtime.
 */
public class ThemeManager {
    private final Parent rootContainer;
    private final CheckBox lightModeToggle;

    private static final String PREF_LIGHT_MODE = "light_mode_enabled";

    /** Persistent storage for the user's theme selection. */
    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);

    /**
     * Constructs a ThemeManager linked to the application's root container.
     * @param rootContainer   The top-level UI node (e.g., VBox or StackPane) where the theme CSS class is applied.
     * @param lightModeToggle The checkbox control used to trigger theme changes.
     */
    public ThemeManager(Parent rootContainer, CheckBox lightModeToggle) {
        this.rootContainer = rootContainer;
        this.lightModeToggle = lightModeToggle;
    }

    /**
     * Synchronizes the UI with the saved theme preference upon startup.
     * Loads the boolean state from the Java Preferences API and applies the
     * corresponding CSS class immediately.
     */
    public void loadSettings() {
        boolean isLightMode = prefs.getBoolean(PREF_LIGHT_MODE, false);
        lightModeToggle.setSelected(isLightMode);
        applyTheme(isLightMode);
    }

    /**
     * Executes a theme switch with a "Fade-In/Fade-Out" animation sequence.
     * This method saves the new preference, fades the root container to transparent,
     * swaps the CSS classes, and then fades the container back to opaque to provide
     * a polished user experience.
     */
    public void handleThemeChange() {
        boolean isLightMode = lightModeToggle.isSelected();
        prefs.putBoolean(PREF_LIGHT_MODE, isLightMode);

        // Fade out the UI to prevent a jarring visual "snap" during CSS re-rendering
        FadeTransition fadeOut = new FadeTransition(Duration.millis(100), rootContainer);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(e -> {
            // Swap the actual CSS classes while the UI is invisible
            applyTheme(isLightMode);

            // Fade back in with the new colors applied
            FadeTransition fadeIn = new FadeTransition(Duration.millis(150), rootContainer);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });

        fadeOut.play();
    }

    /**
     * Dynamically modifies the style class list of the root container.
     * The application's main stylesheet should define selectors for {@code .light-mode}
     * to override default dark-mode variables.
     * @param isLightMode True to apply the light-mode class, false to revert to dark-mode.
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
}
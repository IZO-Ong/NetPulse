package com.izo.netpulse.ui.manager;

import javafx.scene.control.ComboBox;
import java.time.LocalDateTime;

public class TimeRangeManager {

    private final ComboBox<String> comboBox;

    // Define constants for the options to avoid typos
    public static final String LAST_12_HOURS = "Last 12 Hours";
    public static final String LAST_DAY = "Last Day";
    public static final String LAST_WEEK = "Last Week";
    public static final String LAST_MONTH = "Last Month";
    public static final String LAST_YEAR = "Last Year";
    public static final String ALL_TIME = "All Time";

    public TimeRangeManager(ComboBox<String> comboBox) {
        this.comboBox = comboBox;
        setupItems();
    }

    private void setupItems() {
        comboBox.getItems().addAll(
                LAST_12_HOURS, LAST_DAY, LAST_WEEK, LAST_MONTH, LAST_YEAR, ALL_TIME
        );
        comboBox.setValue(LAST_DAY);
    }

    public LocalDateTime getCutoffDate() {
        String selection = comboBox.getValue();
        LocalDateTime now = LocalDateTime.now();

        return switch (selection) {
            case LAST_12_HOURS -> now.minusHours(12);
            case LAST_DAY      -> now.minusDays(1);
            case LAST_WEEK     -> now.minusWeeks(1);
            case LAST_MONTH    -> now.minusMonths(1);
            case LAST_YEAR     -> now.minusYears(1);
            default            -> null; // All Time
        };
    }

    public String getSelectedValue() {
        return comboBox.getValue();
    }
}

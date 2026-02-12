package com.izo.netpulse.ui.manager;

import javafx.scene.control.ComboBox;
import java.time.LocalDateTime;

/**
 * Manages the time-based filtering logic for historical network data.
 * This class coordinates the selection options in a UI ComboBox and calculates
 * the corresponding temporal cutoff points used for database queries and chart filtering.
 */
public class TimeRangeManager {

    private final ComboBox<String> comboBox;

    // Define constants for the options to avoid typos
    public static final String LAST_12_HOURS = "Last 12 Hours";
    public static final String LAST_DAY = "Last Day";
    public static final String LAST_WEEK = "Last Week";
    public static final String LAST_MONTH = "Last Month";
    public static final String LAST_YEAR = "Last Year";
    public static final String ALL_TIME = "All Time";

    /**
     * Constructs a TimeRangeManager and initializes the associated ComboBox items.
     * @param comboBox The JavaFX ComboBox control used for selecting the time range.
     */
    public TimeRangeManager(ComboBox<String> comboBox) {
        this.comboBox = comboBox;
        setupItems();
    }

    /**
     * Populates the ComboBox with localized time range strings and sets
     * the default selection to "Last Day".
     */
    private void setupItems() {
        comboBox.getItems().addAll(
                LAST_12_HOURS, LAST_DAY, LAST_WEEK, LAST_MONTH, LAST_YEAR, ALL_TIME
        );
        comboBox.setValue(LAST_DAY);
    }

    /**
     * Calculates the starting date and time for a filter based on the current UI selection.
     * Uses a switch expression to derive the offset from the current system clock.
     * @return A {@link LocalDateTime} representing the cutoff point for data retrieval,
     * or {@code null} if "All Time" is selected.
     */
    public LocalDateTime getCutoffDate() {
        String selection = comboBox.getValue();
        LocalDateTime now = LocalDateTime.now();

        return switch (selection) {
            case LAST_12_HOURS -> now.minusHours(12);
            case LAST_DAY      -> now.minusDays(1);
            case LAST_WEEK     -> now.minusWeeks(1);
            case LAST_MONTH    -> now.minusMonths(1);
            case LAST_YEAR     -> now.minusYears(1);
            default            -> null; // Signifies no temporal filtering should be applied
        };
    }
}
package com.izo.netpulse.ui.manager;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Manages the visual state and animations of the speed gauge.
 * This class coordinates the length of the progress arc, the coordinates of
 * the needle indicator, and the text value displayed to the user.
 */
public class GaugeManager {
    private final Arc progressArc;
    private final Circle needleCircle;
    private final Label speedValueLabel;

    /** * The current angular sweep of the gauge in degrees.
     * Attached to a listener to update the needle position whenever the value changes.
     */
    private final DoubleProperty currentSweep = new SimpleDoubleProperty(0);

    /**
     * Constructs a GaugeManager and initializes the needle position.
     * @param progressArc The background arc representing the speed progress.
     * @param needleCircle The circular tip of the needle that tracks the speed.
     * @param speedValueLabel The central label displaying the numerical speed.
     */
    public GaugeManager(Arc progressArc, Circle needleCircle, Label speedValueLabel) {
        this.progressArc = progressArc;
        this.needleCircle = needleCircle;
        this.speedValueLabel = speedValueLabel;

        // Ensure the needle always follows the current sweep value, even during animations
        currentSweep.addListener((obs, old, newVal) -> updateNeedlePosition(newVal.doubleValue()));

        updateNeedlePosition(0);
    }

    /**
     * Updates the gauge visuals based on the current throughput.
     * Uses a short Timeline animation to ensure the needle transitions smoothly
     * between sampling intervals.
     * @param mbps The current speed measured in Megabits per second.
     * @param maxSpeed The upper bound of the current gauge scale (e.g., 250 for Download).
     */
    public void updateGauge(double mbps, double maxSpeed) {
        speedValueLabel.setText(String.format("%.1f", mbps));

        // Map speed to a percentage of the 300-degree total arc sweep.
        // The sweep is negative to indicate clockwise movement in JavaFX coordinate space.
        double targetSweep = Math.min(mbps / maxSpeed, 1.0) * -300;

        new Timeline(new KeyFrame(Duration.millis(200),
                new KeyValue(currentSweep, targetSweep))).play();
    }

    /**
     * Creates a Timeline to return the gauge and speed label to zero.
     * Useful for transitions between download and upload phases.
     * @param ms The duration of the reset animation in milliseconds.
     * @return The configured Timeline, ready to be played.
     */
    public Timeline resetGauge(int ms) {
        return new Timeline(new KeyFrame(Duration.millis(ms),
                new KeyValue(currentSweep, 0),
                new KeyValue(speedValueLabel.textProperty(), "0.0")));
    }

    /**
     * Calculates the X and Y coordinates of the needle tip using polar-to-rectangular conversion.
     * The gauge is anchored at a 60-degree start angle with a negative (clockwise) sweep.
     * @param sweep The current angular sweep in degrees.
     */
    private void updateNeedlePosition(double sweep) {
        progressArc.setLength(sweep);

        // Start angle is 60 degrees. JavaFX angles move CCW, so a negative sweep moves CW.
        double angleRad = Math.toRadians(60 + sweep);

        /*
         * Trigonometric Positioning:
         * X = CenterX + Radius * cos(theta)
         * Y = CenterY - Radius * sin(theta) (Inverted because Y increases downwards in JavaFX)
         * Assuming gauge center is (200, 200) with a radius of 120.
         */
        needleCircle.setCenterX(200 + 120 * Math.cos(angleRad));
        needleCircle.setCenterY(200 - 120 * Math.sin(angleRad));
    }
}
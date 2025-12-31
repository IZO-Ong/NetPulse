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

public class GaugeManager {
    private final Arc progressArc;
    private final Circle needleCircle;
    private final Label speedValueLabel;
    private final DoubleProperty currentSweep = new SimpleDoubleProperty(0);

    public GaugeManager(Arc progressArc, Circle needleCircle, Label speedValueLabel) {
        this.progressArc = progressArc;
        this.needleCircle = needleCircle;
        this.speedValueLabel = speedValueLabel;

        currentSweep.addListener((obs, old, newVal) -> updateNeedlePosition(newVal.doubleValue()));

        updateNeedlePosition(0);
    }

    public void updateGauge(double mbps, double maxSpeed) {
        speedValueLabel.setText(String.format("%.1f", mbps));
        double targetSweep = Math.min(mbps / maxSpeed, 1.0) * -300;

        new Timeline(new KeyFrame(Duration.millis(200),
                new KeyValue(currentSweep, targetSweep))).play();
    }

    public Timeline resetGauge(int ms) {
        return new Timeline(new KeyFrame(Duration.millis(ms),
                new KeyValue(currentSweep, 0),
                new KeyValue(speedValueLabel.textProperty(), "0.0")));
    }

    private void updateNeedlePosition(double sweep) {
        progressArc.setLength(sweep);

        // start angle is 60 degrees. Sweep is negative (clockwise).
        double angleRad = Math.toRadians(60 + sweep);

        // Assuming gauge center is 200,200 and radius is 120
        needleCircle.setCenterX(200 + 120 * Math.cos(angleRad));
        needleCircle.setCenterY(200 - 120 * Math.sin(angleRad));
    }
}
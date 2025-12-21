package com.izo.netpulse.ui;

import com.izo.netpulse.service.SpeedTestService;
import com.izo.netpulse.model.SpeedTestResult;
import com.izo.netpulse.repository.SpeedRepository;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.ParallelTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.scene.Group;
import javafx.util.Duration;
import java.util.List;
import javafx.util.StringConverter;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NetPulseController {

    private final SpeedTestService speedService;
    private final SpeedRepository repository;

    @FXML private Arc progressArc;
    @FXML private Circle needleCircle;
    @FXML private Group downloadMarkers;
    @FXML private Group uploadMarkers;
    @FXML private LineChart<Number, Number> historyLineChart;
    @FXML private NumberAxis historyXAxis;
    @FXML private Label statusLabel;
    @FXML private Label speedValueLabel;
    @FXML private Label speedFeedbackLabel;
    @FXML private SVGPath maximizeIcon;
    @FXML private Button actionButton;

    private final DoubleProperty currentSweep = new SimpleDoubleProperty(0);
    private Stage stage;
    private boolean isMaximized = false;
    private boolean isTestRunning = false;
    private double xOffset = 0, yOffset = 0;
    private double prevX, prevY, prevWidth, prevHeight;
    private double activeMaxSpeed = 250.0;

    private static final double MAX_DOWNLOAD_GAUGE = 250.0;
    private static final double MAX_UPLOAD_GAUGE = 100.0;
    private static final String MAXIMIZE_SVG = "M1,1 H9 V9 H1 V1 Z M2,2 V8 H8 V2 H2 Z";
    private static final String RESTORE_SVG = "M2,4 H8 V10 H2 V4 Z M3,5 V9 H7 V5 H3 Z M4,1 H10 V7 H9 V2 H4 V1 Z";

    @FXML
    public void initialize() {
        setupListeners();
        updateNeedlePosition(0);
        refreshHistory();
    }

    private void refreshHistory() {
        List<SpeedTestResult> history = repository.findAll();
        Platform.runLater(() -> loadHistoryData(history));
    }

    public void loadHistoryData(List<SpeedTestResult> historyList) {
        historyLineChart.getData().clear();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");
        historyXAxis.setTickLabelFormatter(new StringConverter<>() { // Fixed with <>
            @Override
            public String toString(Number t) {
                return java.time.Instant.ofEpochSecond(t.longValue())
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(formatter);
            }
            @Override
            public Number fromString(String s) { return 0; }
        });

        XYChart.Series<Number, Number> downloadSeries = new XYChart.Series<>();
        downloadSeries.setName("Download");

        XYChart.Series<Number, Number> uploadSeries = new XYChart.Series<>();
        uploadSeries.setName("Upload");

        for (SpeedTestResult result : historyList) {
            long epochSec = result.getTimestamp()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toEpochSecond();

            downloadSeries.getData().add(new XYChart.Data<>(epochSec, result.getDownloadMbps()));
            uploadSeries.getData().add(new XYChart.Data<>(epochSec, result.getUploadMbps()));
        }

        historyLineChart.getData().add(downloadSeries);
        historyLineChart.getData().add(uploadSeries);
    }

    private void setupListeners() {
        currentSweep.addListener((obs, oldVal, newVal) -> updateNeedlePosition(newVal.doubleValue()));
    }

    @FXML
    private void handleActionButtonClick() {
        if (isTestRunning) cancelTest();
        else startTestSequence();
    }

    private void startTestSequence() {
        isTestRunning = true;
        speedFeedbackLabel.setText("");

        activeMaxSpeed = MAX_DOWNLOAD_GAUGE;
        actionButton.setText("CANCEL TEST");
        statusLabel.setText("Testing Download... (7s)");

        // reset to green for download
        progressArc.getStyleClass().remove("progress-upload");
        needleCircle.getStyleClass().remove("progress-upload-needle");

        if (uploadMarkers.getOpacity() > 0) {
            switchToMarkers(downloadMarkers, uploadMarkers);
        }

        speedService.runDownloadTest(new SpeedTestService.PulseCallback() {
            @Override public void onInstantUpdate(double mbps) { updateGauge(mbps); }
            @Override public void onComplete(double avgDl) {
                if (!isTestRunning) return;

                Platform.runLater(() -> {
                    statusLabel.setText("Preparing Upload...");

                    Timeline reset = resetGaugeForNextPhase();

                    reset.setOnFinished(e -> {
                        if (!isTestRunning) return;

                        activeMaxSpeed = MAX_UPLOAD_GAUGE;
                        switchToMarkers(uploadMarkers, downloadMarkers);

                        // Apply blue theme
                        progressArc.getStyleClass().add("progress-upload");
                        needleCircle.getStyleClass().add("progress-upload-needle");

                        Timeline waitOneSecond = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                            if (!isTestRunning) return;
                            statusLabel.setText("Testing Upload... (7s)");

                            speedService.runUploadTest(new SpeedTestService.PulseCallback() {
                                @Override public void onInstantUpdate(double mbps) {
                                    if (isTestRunning) updateGauge(mbps);
                                }
                                @Override public void onComplete(double avgUl) {
                                    if (isTestRunning) finalizeFullTest(avgDl, avgUl);
                                }
                                @Override public void onError(String msg) { handleTestError(msg); }
                            });
                        }));
                        waitOneSecond.play();
                    });

                    reset.play();
                });
            }
            @Override public void onError(String msg) { handleTestError(msg); }
        });
    }

    private void switchToMarkers(Group toShow, Group toHide) {
        // outgoing
        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), toHide);
        fadeOut.setToValue(0.0);
        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(400), toHide);
        scaleOut.setToX(0.8);
        scaleOut.setToY(0.8);
        // incoming
        toShow.setOpacity(0);
        toShow.setScaleX(0.8);
        toShow.setScaleY(0.8);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), toShow);
        fadeIn.setToValue(1.0);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(400), toShow);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);

        new ParallelTransition(fadeOut, scaleOut, fadeIn, scaleIn).play();
    }

    private void handleTestError(String error) {
        isTestRunning = false;
        Platform.runLater(() -> { statusLabel.setText("Error: " + error); actionButton.setText("RUN TEST"); resetGauge(); });
    }

    private Timeline resetGaugeForNextPhase() {
        return new Timeline(new KeyFrame(Duration.millis(800),
                new KeyValue(currentSweep, 0),
                new KeyValue(speedValueLabel.textProperty(), "0.0")
        ));
    }

    private void finalizeFullTest(double dl, double ul) {
        isTestRunning = false;
        speedService.saveResult(dl, ul);
        Platform.runLater(() -> {
            actionButton.getStyleClass().remove("button-cancel");
            statusLabel.setText(String.format("DL: %.1f Mbps | UL: %.1f Mbps", dl, ul));

            // Update feedback based on download speed
            updateSpeedFeedback(dl);

            actionButton.setText("RUN TEST");

            resetGauge();
            refreshHistory();
        });
    }

    private void updateSpeedFeedback(double dl) {
        String feedback;
        if (dl >= 200) {
            feedback = "Your Internet connection is very fast. It should be able to handle multiple devices streaming 4K/HD videos, video conferencing, and gaming at the same time.";
        } else if (dl >= 150) {
            feedback = "Your connection is fast. Great for high-quality streaming and smooth gaming on several devices simultaneously.";
        } else if (dl >= 100) {
            feedback = "Good connection. Sufficient for HD streaming and standard online activities for a small household.";
        } else if (dl >= 50) {
            feedback = "Basic connection. Suitable for single-device HD streaming and general web browsing.";
        } else {
            feedback = "Slow connection. You may experience buffering during HD video playback or lag during online gaming.";
        }
        speedFeedbackLabel.setText(feedback);
    }

    private void cancelTest() {
        isTestRunning = false;
        speedService.stopTest();

        Platform.runLater(() -> {
            actionButton.getStyleClass().remove("button-cancel");
            actionButton.setText("RUN TEST");
            statusLabel.setText("Test Cancelled");

            resetGauge();
        });
    }

    private void updateGauge(double instantMbps) {
        speedValueLabel.setText(String.format("%.1f", instantMbps));
        double targetSweep = Math.min(instantMbps / activeMaxSpeed, 1.0) * -300;
        new Timeline(new KeyFrame(Duration.millis(200), new KeyValue(currentSweep, targetSweep))).play();
    }

    private void resetGauge() {
        new Timeline(new KeyFrame(Duration.millis(800), new KeyValue(currentSweep, 0), new KeyValue(speedValueLabel.textProperty(), "0.0"))).play();
    }

    private void updateNeedlePosition(double sweep) {
        progressArc.setLength(sweep);
        double angleRad = Math.toRadians(60 + sweep);
        needleCircle.setCenterX(200 + 120 * Math.cos(angleRad));
        needleCircle.setCenterY(200 - 120 * Math.sin(angleRad));
    }

    @FXML
    private void handleHoverStart() {
        if (isTestRunning) {
            actionButton.setText("CANCEL TEST");
            if (!actionButton.getStyleClass().contains("button-cancel")) {
                actionButton.getStyleClass().add("button-cancel");
            }
        }
    }
    @FXML
    private void handleHoverEnd() {
        actionButton.setText(isTestRunning ? "CANCEL TEST" : "RUN TEST");

        actionButton.getStyleClass().remove("button-cancel");
    }

    @FXML public void handleMousePressed(MouseEvent event) { ensureStage(event); if (!isMaximized) { xOffset = event.getSceneX(); yOffset = event.getSceneY(); } }
    @FXML public void handleMouseDragged(MouseEvent event) { if (stage != null && !isMaximized) { stage.setX(event.getScreenX() - xOffset); stage.setY(event.getScreenY() - yOffset); } }
    @FXML public void handleMinimise(ActionEvent event) { ensureStage(event); stage.setIconified(true); }
    @FXML public void handleMaximize(ActionEvent event) { ensureStage(event); if (!isMaximized) maximizeWindow(); else restoreWindow(); }

    private void maximizeWindow() {
        prevX = stage.getX(); prevY = stage.getY(); prevWidth = stage.getWidth(); prevHeight = stage.getHeight();
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX()); stage.setY(bounds.getMinY()); stage.setWidth(bounds.getWidth()); stage.setHeight(bounds.getHeight());
        isMaximized = true; maximizeIcon.setContent(RESTORE_SVG);
    }

    private void restoreWindow() {
        stage.setX(prevX); stage.setY(prevY); stage.setWidth(prevWidth); stage.setHeight(prevHeight);
        isMaximized = false; maximizeIcon.setContent(MAXIMIZE_SVG);
    }

    @FXML public void handleClose() { Platform.exit(); System.exit(0); }
    private void ensureStage(javafx.event.Event event) { if (stage == null) stage = (Stage) ((Node) event.getSource()).getScene().getWindow(); }
}
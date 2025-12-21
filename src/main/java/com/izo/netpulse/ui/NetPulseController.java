package com.izo.netpulse.ui;

import com.izo.netpulse.model.SpeedTestResult;
import com.izo.netpulse.repository.SpeedRepository;
import com.izo.netpulse.service.SpeedFeedbackService;
import com.izo.netpulse.service.SpeedTestService;
import com.izo.netpulse.service.BackgroundMonitorService;
import com.izo.netpulse.ui.manager.GaugeManager;
import com.izo.netpulse.ui.manager.WindowManager;
import com.izo.netpulse.ui.manager.TimeRangeManager;
import com.izo.netpulse.ui.manager.BackgroundMonitorManager;
import com.izo.netpulse.ui.util.AnimationUtility;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NetPulseController {

    // services
    private final SpeedTestService speedService;
    private final SpeedRepository repository;
    private final SpeedFeedbackService feedbackService;
    private final BackgroundMonitorService backgroundMonitorService;

    // managers
    private GaugeManager gaugeManager;
    private TimeRangeManager timeRangeManager;
    private BackgroundMonitorManager monitorManager;
    private final WindowManager windowManager = new WindowManager();

    // FXML components
    @FXML private Arc progressArc;
    @FXML private Circle needleCircle;
    @FXML private Group downloadMarkers;
    @FXML private Group uploadMarkers;
    @FXML private LineChart<Number, Number> historyLineChart;
    @FXML private ComboBox<String> timeRangeSelector;
    @FXML private NumberAxis historyXAxis;
    @FXML private Label statusLabel;
    @FXML private Label speedValueLabel;
    @FXML private Label speedFeedbackLabel;
    @FXML private SVGPath maximizeIcon;
    @FXML private Button actionButton;
    @FXML private CheckBox backgroundMonitorToggle;
    @FXML private ComboBox<String> monitorIntervalSelector;

    // state variables
    private boolean isTestRunning = false;
    private double activeMaxSpeed = 250.0;
    private static final double MAX_DOWNLOAD_GAUGE = 250.0;
    private static final double MAX_UPLOAD_GAUGE = 100.0;

    @FXML
    public void initialize() {
        gaugeManager = new GaugeManager(progressArc, needleCircle, speedValueLabel);
        timeRangeManager = new TimeRangeManager(timeRangeSelector);

        // Everything related to background monitoring happens inside this one call
        monitorManager = new BackgroundMonitorManager(
                backgroundMonitorService,
                backgroundMonitorToggle,
                monitorIntervalSelector,
                this::refreshHistory
        );

        refreshHistory();
    }

    @FXML
    private void handleTimeRangeChange() {
        refreshHistory();
    }

    //TEST EXECUTION

    @FXML
    private void handleActionButtonClick() {
        if (isTestRunning) {
            cancelTest();
        } else {
            startTestSequence();
        }
    }

    private void startTestSequence() {
        isTestRunning = true;

        if (!actionButton.getStyleClass().contains("button-cancel")) {
            actionButton.getStyleClass().add("button-cancel");
        }

        prepareUIForDownload();

        speedService.runDownloadTest(new SpeedTestService.TestCallback() {
            @Override
            public void onInstantUpdate(double mbps) {
                gaugeManager.updateGauge(mbps, activeMaxSpeed);
            }

            @Override
            public void onComplete(double avgDl) {
                if (!isTestRunning) return;
                Platform.runLater(() -> startUploadTransition(avgDl));
            }

            @Override
            public void onError(String msg) {
                handleTestError(msg);
            }
        });
    }

    private void startUploadTransition(double avgDl) {
        statusLabel.setText("Preparing Upload...");

        Timeline reset = gaugeManager.resetGauge(800);

        reset.setOnFinished(e -> {
            if (!isTestRunning) return;

            // switch to upload
            activeMaxSpeed = MAX_UPLOAD_GAUGE;
            AnimationUtility.switchToMarkers(uploadMarkers, downloadMarkers);

            // blue css
            progressArc.getStyleClass().add("progress-upload");
            needleCircle.getStyleClass().add("progress-upload-needle");

            new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                if (!isTestRunning) return;
                statusLabel.setText("Testing Upload... (7s)");

                speedService.runUploadTest(new SpeedTestService.TestCallback() {
                    @Override
                    public void onInstantUpdate(double mbps) {
                        if (isTestRunning) gaugeManager.updateGauge(mbps, activeMaxSpeed);
                    }

                    @Override
                    public void onComplete(double avgUl) {
                        if (isTestRunning) finalizeFullTest(avgDl, avgUl);
                    }

                    @Override
                    public void onError(String msg) {
                        handleTestError(msg);
                    }
                });
            })).play();
        });

        reset.play();
    }

    private void finalizeFullTest(double dl, double ul) {
        isTestRunning = false;
        speedService.saveResult(dl, ul);
        Platform.runLater(() -> {
            actionButton.getStyleClass().remove("button-cancel");
            actionButton.setText("RUN TEST");
            statusLabel.setText(String.format("DL: %.1f Mbps | UL: %.1f Mbps", dl, ul));
            speedFeedbackLabel.setText(feedbackService.getFeedback(dl));

            gaugeManager.resetGauge(800).play();
            refreshHistory();
        });
    }

    private void cancelTest() {
        isTestRunning = false;
        speedService.stopTest();

        Platform.runLater(() -> {
            if (activeMaxSpeed == MAX_UPLOAD_GAUGE || uploadMarkers.getOpacity() > 0) {
                AnimationUtility.switchToMarkers(downloadMarkers, uploadMarkers);
            }

            activeMaxSpeed = MAX_DOWNLOAD_GAUGE;
            actionButton.getStyleClass().remove("button-cancel");
            actionButton.setText("RUN TEST");
            statusLabel.setText("Test Cancelled");

            progressArc.getStyleClass().remove("progress-upload");
            needleCircle.getStyleClass().remove("progress-upload-needle");

            gaugeManager.resetGauge(800).play();
        });
    }

    private void prepareUIForDownload() {
        activeMaxSpeed = MAX_DOWNLOAD_GAUGE;
        speedFeedbackLabel.setText("");
        actionButton.setText("CANCEL TEST");
        statusLabel.setText("Testing Download... (7s)");

        progressArc.getStyleClass().remove("progress-upload");
        needleCircle.getStyleClass().remove("progress-upload-needle");

        if (uploadMarkers.getOpacity() > 0) {
            AnimationUtility.switchToMarkers(downloadMarkers, uploadMarkers);
        }
    }

    private void handleTestError(String error) {
        isTestRunning = false;
        Platform.runLater(() -> {
            statusLabel.setText("Error: " + error);
            actionButton.setText("RUN TEST");
            gaugeManager.resetGauge(800).play();
        });
    }

    // HISTORY CHART LOGIC

    private void refreshHistory() {
        List<SpeedTestResult> allResults = repository.findAll();
        LocalDateTime cutoff = timeRangeManager.getCutoffDate();

        List<SpeedTestResult> filteredResults = allResults.stream()
                .filter(r -> cutoff == null || r.getTimestamp().isAfter(cutoff))
                .toList();

        Platform.runLater(() -> loadHistoryData(filteredResults));
    }

    public void loadHistoryData(List<SpeedTestResult> historyList) {
        historyLineChart.getData().clear();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm");

        historyXAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number t) {
                return java.time.Instant.ofEpochSecond(t.longValue())
                        .atZone(java.time.ZoneId.systemDefault()).format(formatter);
            }
            @Override public Number fromString(String s) { return 0; }
        });

        XYChart.Series<Number, Number> downloadSeries = new XYChart.Series<>();
        downloadSeries.setName("Download");
        XYChart.Series<Number, Number> uploadSeries = new XYChart.Series<>();
        uploadSeries.setName("Upload");

        for (SpeedTestResult result : historyList) {
            long epoch = result.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
            downloadSeries.getData().add(new XYChart.Data<>(epoch, result.getDownloadMbps()));
            uploadSeries.getData().add(new XYChart.Data<>(epoch, result.getUploadMbps()));
        }

        historyLineChart.getData().add(downloadSeries);
        historyLineChart.getData().add(uploadSeries);
    }

    // BG MONITOR LOGIC
    @FXML
    private void handleMonitorSettingsChange() {
        monitorManager.handleSettingsChange();
    }

    // WINDOW AND EVENT HANDLERS

    @FXML public void handleMousePressed(MouseEvent event) { windowManager.handlePressed(event); }

    @FXML public void handleMouseDragged(MouseEvent event) {
        windowManager.handleDragged(event, getStage(event));
    }

    @FXML public void handleMinimise(ActionEvent event) { getStage(event).setIconified(true); }

    @FXML public void handleMaximize(ActionEvent event) {
        windowManager.toggleMaximize(getStage(event), maximizeIcon);
    }

    @FXML public void handleClose() { Platform.exit(); System.exit(0); }

    private Stage getStage(javafx.event.Event event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }
}
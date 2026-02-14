package com.izo.netpulse.ui;

import com.izo.netpulse.model.SpeedTestResult;
import com.izo.netpulse.repository.SpeedRepository;
import com.izo.netpulse.service.DiagnosticService;
import com.izo.netpulse.service.SpeedFeedbackService;
import com.izo.netpulse.service.SpeedTestService;
import com.izo.netpulse.service.BackgroundMonitorService;
import com.izo.netpulse.ui.manager.*;
import com.izo.netpulse.ui.util.AnimationUtility;

import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Primary UI Controller for the NetPulse application.
 * Handles the orchestration between the Spring Boot backend services and the JavaFX frontend components.
 * Manages speed testing sequences, diagnostic scans, history visualization, and system theming.
 */
@Component
@RequiredArgsConstructor
public class NetPulseController {

    private final SpeedTestService speedService;
    private final SpeedRepository repository;
    private final SpeedFeedbackService feedbackService;
    private final BackgroundMonitorService monitorService;
    @Autowired private DiagnosticService diagnosticService;

    // UI Logic Managers
    private GaugeManager gaugeManager;
    private TimeRangeManager timeRangeManager;
    private ThemeManager themeManager;
    private BackgroundMonitorManager monitorManager;

    // FXML UI Components
    @FXML private VBox mainContainer;
    @FXML private Arc progressArc;
    @FXML private Circle needleCircle;
    @FXML private Group downloadMarkers;
    @FXML private Group uploadMarkers;
    @FXML private TextArea diagnosticsArea;
    @FXML private LineChart<Number, Number> historyLineChart;
    @FXML private ComboBox<String> timeRangeSelector;
    @FXML private NumberAxis historyXAxis;
    @FXML private Label statusLabel;
    @FXML private Label speedValueLabel;
    @FXML private Label speedFeedbackLabel;
    @FXML private SVGPath maximizeIcon;
    @FXML private Button actionButton;
    @FXML private Button diagButton;
    @FXML private CheckBox lightModeToggle;
    @FXML private CheckBox backgroundMonitorToggle;
    @FXML private ComboBox<String> monitorIntervalSelector;

    // State Variables
    private boolean isTestRunning = false;
    private double activeMaxSpeed = 250.0;
    private static final double MAX_DOWNLOAD_GAUGE = 250.0;
    private static final double MAX_UPLOAD_GAUGE = 100.0;

    /**
     * Initializes the controller after its root element has been completely processed.
     * Sets up the managers for gauges, history filters, themes, and background monitoring.
     */
    @FXML
    public void initialize() {
        gaugeManager = new GaugeManager(progressArc, needleCircle, speedValueLabel);
        timeRangeManager = new TimeRangeManager(timeRangeSelector);
        themeManager = new ThemeManager(mainContainer, lightModeToggle);
        monitorManager = new BackgroundMonitorManager(monitorService, backgroundMonitorToggle, monitorIntervalSelector, this::refreshHistory);

        themeManager.loadSettings();
        monitorManager.loadSettings();
        refreshHistory();
    }

    /**
     * Handles the primary action button click.
     * Toggles between starting a new speed test sequence and cancelling an active one.
     */
    @FXML
    private void handleActionButtonClick() {
        if (isTestRunning) cancelTest(); else startTestSequence();
    }

    /**
     * Initiates the multi-stage speed test sequence starting with Download.
     * Configures UI state and hooks into the SpeedTestService callbacks.
     */
    private void startTestSequence() {
        isTestRunning = true;
        if (!actionButton.getStyleClass().contains("button-cancel")) {
            actionButton.getStyleClass().add("button-cancel");
        }
        prepareUIForDownload();

        speedService.runDownloadTest(new SpeedTestService.TestCallback() {
            @Override
            public void onInstantUpdate(double mbps) {
                Platform.runLater(() -> gaugeManager.updateGauge(mbps, activeMaxSpeed));
            }
            @Override
            public void onComplete(double avgDl) {
                if (!isTestRunning) return;
                Platform.runLater(() -> startUploadTransition(avgDl));
            }
            @Override
            public void onError(String msg) { handleTestError(msg); }
        });
    }

    /**
     * Transitions the UI from Download mode to Upload mode.
     * Resets the gauge and measures latency before initiating the upload test.
     * @param avgDl The calculated average download speed in Mbps.
     */
    private void startUploadTransition(double avgDl) {
        statusLabel.setText("Preparing Upload...");
        Timeline reset = gaugeManager.resetGauge(800);
        reset.setOnFinished(e -> {
            if (!isTestRunning) return;

            activeMaxSpeed = MAX_UPLOAD_GAUGE;
            AnimationUtility.switchToMarkers(uploadMarkers, downloadMarkers);
            progressArc.getStyleClass().add("progress-upload");
            needleCircle.getStyleClass().add("progress-upload-needle");

            speedService.measureLatencyAverage(() -> {
                if (!isTestRunning) return;
                statusLabel.setText("Testing Upload... (7s)");
                speedService.runUploadTest(new SpeedTestService.TestCallback() {
                    @Override
                    public void onInstantUpdate(double mbps) {
                        Platform.runLater(() -> gaugeManager.updateGauge(mbps, activeMaxSpeed));
                    }
                    @Override
                    public void onComplete(double avgUl) {
                        if (isTestRunning) finalizeFullTest(avgDl, avgUl);
                    }
                    @Override
                    public void onError(String msg) { handleTestError(msg); }
                });
            }, () -> handleTestError("Latency Check Failed: No Connection."));
        });
        reset.play();
    }

    /**
     * Concludes the speed test, saves results to the local database, and updates the history view.
     * @param dl Final average download speed.
     * @param ul Final average upload speed.
     */
    private void finalizeFullTest(double dl, double ul) {
        isTestRunning = false;
        double latency = speedService.getCurrentLatency();
        speedService.saveResult(dl, ul);

        Platform.runLater(() -> {
            actionButton.getStyleClass().remove("button-cancel");
            actionButton.setText("RUN TEST");
            statusLabel.setText(String.format("DL: %.1f Mbps | UL: %.1f Mbps\nLatency: %.0f ms", dl, ul, latency));
            speedFeedbackLabel.setText(feedbackService.getFeedback(dl));
            gaugeManager.resetGauge(800).play();
            refreshHistory();
        });
    }

    /**
     * Cancels any active network operations and reverts the UI to the default state.
     */
    private void cancelTest() {
        isTestRunning = false;
        speedService.stopTest();
        Platform.runLater(() -> {
            resetUIToDefault();
            statusLabel.setText("Test Cancelled");
        });
    }

    /**
     * Handles fatal errors during the test sequence.
     * @param error The error message to display to the user.
     */
    private void handleTestError(String error) {
        isTestRunning = false;
        speedService.stopTest();
        Platform.runLater(() -> {
            resetUIToDefault();
            statusLabel.setText(error);
        });
    }

    /**
     * Resets visual components (markers, gauges, CSS classes) to their default Download states.
     */
    private void resetUIToDefault() {
        actionButton.getStyleClass().remove("button-cancel");
        actionButton.setText("RUN TEST");

        if (activeMaxSpeed == MAX_UPLOAD_GAUGE || uploadMarkers.getOpacity() > 0) {
            AnimationUtility.switchToMarkers(downloadMarkers, uploadMarkers);
        }

        activeMaxSpeed = MAX_DOWNLOAD_GAUGE;
        progressArc.getStyleClass().removeAll("progress-upload", "progress-upload-needle");
        needleCircle.getStyleClass().removeAll("progress-upload-needle");
        gaugeManager.resetGauge(800).play();
    }

    /**
     * Prepares the UI specifically for the Download phase of the test.
     */
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

    /** Handles theme toggle events between light and dark modes. */
    @FXML private void handleThemeChange() { themeManager.handleThemeChange(); }

    /** Handles updates to background monitoring intervals and activation. */
    @FXML private void handleMonitorSettingsChange() { monitorManager.handleMonitorSettingsChange(); }

    /** Triggered when the user changes the historical data filter range. */
    @FXML private void handleTimeRangeChange() { refreshHistory(); }

    /**
     * Fetches results from the repository, applies time filters, and updates the chart.
     */
    private void refreshHistory() {
        List<SpeedTestResult> allResults = repository.findAll();
        LocalDateTime cutoff = timeRangeManager.getCutoffDate();
        List<SpeedTestResult> filtered = allResults.stream()
                .filter(r -> cutoff == null || r.getTimestamp().isAfter(cutoff))
                .toList();
        Platform.runLater(() -> loadHistoryData(filtered));
    }

    /**
     * Loads filtered results into the JavaFX LineChart.
     * @param historyList List of SpeedTestResult entities to visualize.
     */
    private void loadHistoryData(List<SpeedTestResult> historyList) {
        historyLineChart.getData().clear();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm");

        historyXAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number t) {
                return java.time.Instant.ofEpochSecond(t.longValue())
                        .atZone(java.time.ZoneId.systemDefault()).format(formatter);
            }
            @Override public Number fromString(String s) { return 0; }
        });

        XYChart.Series<Number, Number> dlSeries = new XYChart.Series<>();
        dlSeries.setName("Download");
        XYChart.Series<Number, Number> ulSeries = new XYChart.Series<>();
        ulSeries.setName("Upload");

        for (SpeedTestResult result : historyList) {
            long epoch = result.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
            dlSeries.getData().add(new XYChart.Data<>(epoch, result.getDownloadMbps()));
            ulSeries.getData().add(new XYChart.Data<>(epoch, result.getUploadMbps()));
        }
        historyLineChart.getData().add(dlSeries);
        historyLineChart.getData().add(ulSeries);
    }

    /**
     * Executes a comprehensive network diagnostic scan in a background thread.
     * Scans local adapters, public ISP data, DNS resolution, and global latency.
     */
    @FXML
    public void runDiagnostics() {
        diagButton.setDisable(true);
        diagnosticsArea.clear();
        updateDiagnosticsArea(">>> Starting Network Scan...");

        Thread diagThread = new Thread(() -> {
            try {
                updateDiagnosticsArea("Local Adapter: " + diagnosticService.getActiveInterface());
                updateDiagnosticsArea("\nLocating Public ISP...");
                updateDiagnosticsArea(diagnosticService.getServerLocation());
                updateDiagnosticsArea("\nDNS Configuration: " + diagnosticService.getDnsServers());
                updateDiagnosticsArea("DNS Resolution Speed (google.com): " + diagnosticService.testDnsSpeed("google.com"));
                updateDiagnosticsArea("Web Connectivity (Port 80): " + diagnosticService.checkWebReachability());
                updateDiagnosticsArea("\nIdentifying First Hop (Gateway)...");
                updateDiagnosticsArea("Router Response: " + diagnosticService.getFirstHop());
                updateDiagnosticsArea("\nTesting Global Latency...");
                updateDiagnosticsArea("Google (8.8.8.8): " + diagnosticService.getGlobalPing("8.8.8.8"));
                updateDiagnosticsArea("Cloudflare (1.1.1.1): " + diagnosticService.getGlobalPing("1.1.1.1"));

                Platform.runLater(() -> {
                    diagnosticsArea.appendText("\n>>> Scan Complete.");
                    diagButton.setDisable(false);
                });
            } catch (Exception e) {
                updateDiagnosticsArea("\n[CRITICAL ERROR]: " + e.getMessage());
                Platform.runLater(() -> diagButton.setDisable(false));
            }
        });
        diagThread.setDaemon(true);
        diagThread.start();
    }

    /** Updates the diagnostic text area safely from a background thread. */
    private void updateDiagnosticsArea(String text) { Platform.runLater(() -> diagnosticsArea.appendText(text + "\n")); }

    /** Closes the application and shuts down the JVM. */
    @FXML public void handleClose() { Platform.exit(); System.exit(0); }

    /** Utility to retrieve the Stage from an event source. */
    private Stage getStage(javafx.event.Event event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }
}
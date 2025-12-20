package com.izo.netpulse.ui;

import com.izo.netpulse.service.SpeedTestService;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Arc;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

@Component
@RequiredArgsConstructor
public class NetPulseController {
    private final SpeedTestService speedService;

    @FXML private Arc progressArc;
    @FXML private Circle needleCircle;
    @FXML private LineChart<String, Number> speedChart;
    @FXML private Label statusLabel;
    @FXML private Label speedValueLabel;
    @FXML private SVGPath maximizeIcon;

    private final XYChart.Series<String, Number> series = new XYChart.Series<>();
    private Stage stage;
    private double xOffset = 0, yOffset = 0;
    private boolean isMaximized = false;
    private double prevX, prevY, prevWidth, prevHeight;
    private static final double MAX_SPEED = 250.0;
    private final DoubleProperty currentSweep = new SimpleDoubleProperty(0);
    private static final String MAXIMIZE_SVG = "M1,1 H9 V9 H1 V1 Z M2,2 V8 H8 V2 H2 Z";
    private static final String RESTORE_SVG = "M2,4 H8 V10 H2 V4 Z M3,5 V9 H7 V5 H3 Z M4,1 H10 V7 H9 V2 H4 V1 Z";

    @FXML
    public void initialize() {
        if (speedChart.getData().isEmpty()) {
            series.setName("Mbps Download");
            speedChart.getData().add(series);
        }

        currentSweep.addListener((obs, oldVal, newVal) -> {
            updateNeedlePosition(newVal.doubleValue());
        });

        updateNeedlePosition(0);
    }

    private void updateNeedlePosition(double sweep) {
        progressArc.setLength(sweep);

        double angleRad = Math.toRadians(60 + sweep);

        needleCircle.setTranslateX(120 * Math.cos(angleRad));
        needleCircle.setTranslateY(-120 * Math.sin(angleRad));
    }

    @FXML
    public void handleMousePressed(MouseEvent event) {
        if (stage == null) {
            stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        }

        if (isMaximized) return;

        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

    @FXML
    public void handleMouseDragged(MouseEvent event) {
        if (stage == null || isMaximized) return;
        stage.setX(event.getScreenX() - xOffset);
        stage.setY(event.getScreenY() - yOffset);
    }

    @FXML
    public void handleMinimise(ActionEvent event) {
        if (stage == null) stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    public void handleMaximize(ActionEvent event) {
        if (stage == null) stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

        if (!isMaximized) {
            prevX = stage.getX(); prevY = stage.getY();
            prevWidth = stage.getWidth(); prevHeight = stage.getHeight();
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();

            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());

            isMaximized = true;
            maximizeIcon.setContent(RESTORE_SVG);
        } else {
            stage.setX(prevX);
            stage.setY(prevY);
            stage.setWidth(prevWidth);
            stage.setHeight(prevHeight);
            maximizeIcon.setContent(MAXIMIZE_SVG);
            isMaximized = false;
        }
    }

    @FXML
    public void handleClose() {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    public void handleStartTest() {
        statusLabel.setText("Testing download... (7s test)");
        speedService.runPulseTest(new SpeedTestService.PulseCallback() {
            @Override
            public void onInstantUpdate(double instantMbps) {
                Platform.runLater(() -> {
                    speedValueLabel.setText(String.format("%.1f", instantMbps));

                    double ratio = Math.min(instantMbps / MAX_SPEED, 1.0);
                    double targetSweep = ratio * -300;

                    Timeline anim = new Timeline(new KeyFrame(Duration.millis(200),
                            new KeyValue(currentSweep, targetSweep)));
                    anim.play();
                });
            }

            @Override
            public void onComplete(double averageMbps) {
                Platform.runLater(() -> {
                    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    series.getData().add(new XYChart.Data<>(time, averageMbps));
                    statusLabel.setText("Result: " + String.format("%.2f", averageMbps) + " Mbps");

                    Timeline resetDelay = new Timeline(new KeyFrame(Duration.seconds(0.2), e -> {
                        resetGauge();
                    }));
                    resetDelay.play();
                });
            }

            private void resetGauge() {
                Timeline reset = new Timeline(new KeyFrame(Duration.millis(800),
                        new KeyValue(currentSweep, 0),
                        new KeyValue(speedValueLabel.textProperty(), "0.0")
                ));
                reset.play();
            }
        });
    }
}
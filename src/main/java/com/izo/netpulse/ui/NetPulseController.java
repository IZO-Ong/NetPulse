package com.izo.netpulse.ui;

import com.izo.netpulse.service.SpeedTestService;
import javafx.scene.shape.Circle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class NetPulseController {
    private final SpeedTestService speedService;

    @FXML private Circle progressCircle;
    @FXML private LineChart<String, Number> speedChart;
    @FXML private Label statusLabel;
    @FXML private Label speedValueLabel;

    private final XYChart.Series<String, Number> series = new XYChart.Series<>();
    private Stage stage;
    private double xOffset = 0, yOffset = 0;
    private boolean isMaximized = false;
    private double prevX, prevY, prevWidth, prevHeight;
    private static final double MAX_SPEED = 200.0;
    private static final double CIRCUMFERENCE = 754.0;

    @FXML
    public void initialize() {
        series.setName("Mbps Download");
        speedChart.getData().add(series);

        Platform.runLater(() -> {
            if (speedValueLabel.getScene() != null) {
                stage = (Stage) speedValueLabel.getScene().getWindow();

                stage.iconifiedProperty().addListener((obs, wasMinimized, isNowMinimized) -> {
                    if (!isNowMinimized) {
                        stage.requestFocus();
                        stage.getScene().getRoot().requestLayout();
                    }
                });
            }
        });
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
        } else {
            stage.setX(prevX);
            stage.setY(prevY);
            stage.setWidth(prevWidth);
            stage.setHeight(prevHeight);
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
        statusLabel.setText("Pulsing... (7s test)");

        speedService.runPulseTest(new SpeedTestService.PulseCallback() {
            @Override
            public void onInstantUpdate(double instantMbps) {
                Platform.runLater(() -> {
                    speedValueLabel.setText(String.format("%.1f", instantMbps));
                    double ratio = Math.min(instantMbps / MAX_SPEED, 1.0);
                    double targetOffset = CIRCUMFERENCE * (1.0 - ratio);
                    progressCircle.setStrokeDashOffset(targetOffset);
                });
            }

            @Override
            public void onComplete(double averageMbps) {
                Platform.runLater(() -> {
                    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    series.getData().add(new XYChart.Data<>(time, averageMbps));
                    statusLabel.setText("Result: " + String.format("%.2f", averageMbps) + " Mbps");
                });
            }
        });
    }
}
package com.izo.netpulse.ui;

import com.izo.netpulse.service.SpeedTestService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.KeyValue;
import javafx.scene.shape.Circle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NetPulseController {
    private final SpeedTestService speedService;
    
    @FXML private Circle progressCircle;
    @FXML private LineChart<String, Number> speedChart;
    @FXML private Label statusLabel;
    @FXML private Label speedValueLabel;
    
    private XYChart.Series<String, Number> series = new XYChart.Series<>();
    private double xOffset = 0, yOffset = 0, targetX = 0, targetY = 0;
    private Timeline smoothMoveTimeline;
    private Stage stage;
    private boolean isMaximized = false;
    private double prevX, prevY, prevWidth, prevHeight;

    @FXML
    public void initialize() {
        series.setName("Mbps Download");
        speedChart.getData().add(series);

        smoothMoveTimeline = new Timeline(new KeyFrame(Duration.millis(16), event -> {
            if (stage == null || isMaximized || stage.isIconified()) {
                smoothMoveTimeline.stop();
                return;
            }
            double curX = stage.getX();
            double curY = stage.getY();
            stage.setX(curX + (targetX - curX) * 0.4);
            stage.setY(curY + (targetY - curY) * 0.4);
        }));
        smoothMoveTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    @FXML
    public void handleMousePressed(MouseEvent event) {
        if (stage == null) stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        if (isMaximized) return;
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
        targetX = stage.getX();
        targetY = stage.getY();
        smoothMoveTimeline.play();
    }

    @FXML
    public void handleMouseDragged(MouseEvent event) {
        if (!isMaximized) {
            targetX = event.getScreenX() - xOffset;
            targetY = event.getScreenY() - yOffset;
        }
    }

    @FXML
    public void handleMouseReleased(MouseEvent event) {
        smoothMoveTimeline.stop();
    }

    @FXML
    public void handleMinimise(ActionEvent event) {
        smoothMoveTimeline.stop();
        Stage s = (Stage) ((Node) event.getSource()).getScene().getWindow();
        s.setIconified(true);
    }

    @FXML
    public void handleMaximize(ActionEvent event) {
        smoothMoveTimeline.stop();
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
        smoothMoveTimeline.stop();
        Platform.exit();
        System.exit(0);
    }

    public void animateSpeedResult(double targetSpeed) {
        speedValueLabel.setText("0.0");
        progressCircle.setStrokeDashOffset(754);

        Timeline numTimeline = new Timeline();
        for (int i = 0; i <= 100; i++) {
            double currentVal = (targetSpeed / 100.0) * i;
            numTimeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(i * 15), e -> 
                    speedValueLabel.setText(String.format("%.1f", currentVal))
                )
            );
        }

        double targetOffset = 754 - (754 * Math.min(targetSpeed / 100.0, 1.0)); 
        Timeline circleTimeline = new Timeline(
            new KeyFrame(Duration.seconds(1.5), 
                new KeyValue(progressCircle.strokeDashOffsetProperty(), targetOffset)
            )
        );

        numTimeline.play();
        circleTimeline.play();
    }

    @FXML
    public void handleStartTest() {
        statusLabel.setText("Pulse initiated...");
        new Thread(() -> {
            try {
                var result = speedService.runTest();
                Platform.runLater(() -> {
                    animateSpeedResult(result.getDownloadMbps());
                    
                    series.getData().add(new XYChart.Data<>(
                        result.getTimestamp().toLocalTime().toString().substring(0, 8), 
                        result.getDownloadMbps()
                    ));
                    statusLabel.setText("Last Result: " + String.format("%.2f", result.getDownloadMbps()) + " Mbps");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error: Connection Failed"));
            }
        }).start();
    }
}
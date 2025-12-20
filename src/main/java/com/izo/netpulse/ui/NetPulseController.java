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
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
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
    
    private XYChart.Series<String, Number> series = new XYChart.Series<>();
    private double xOffset = 0, yOffset = 0, targetX = 0, targetY = 0;
    private Timeline smoothMoveTimeline;
    private Stage stage;
    private boolean isMaximized = false;
    private double prevX, prevY, prevWidth, prevHeight;
    private static final double MAX_SPEED = 200.0;
    private static final double CIRCUMFERENCE = 754.0; // 2 * PI * radius(120)

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
        statusLabel.setText("Pulsing... (7s test)");

        speedService.runPulseTest(new SpeedTestService.PulseCallback() {
            @Override
            public void onInstantUpdate(double instantMbps) {
                speedValueLabel.setText(String.format("%.1f", instantMbps));

                double ratio = Math.min(instantMbps / MAX_SPEED, 1.0);
                double targetOffset = CIRCUMFERENCE * (1.0 - ratio);

                Timeline t = new Timeline(new KeyFrame(Duration.millis(200),
                        new KeyValue(progressCircle.strokeDashOffsetProperty(), targetOffset)
                ));
                t.play();
            }

            @Override
            public void onComplete(double averageMbps) {
                String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                series.getData().add(new XYChart.Data<>(time, averageMbps));

                statusLabel.setText("Result: " + String.format("%.2f", averageMbps) + " Mbps");
            }
        });
    }
}
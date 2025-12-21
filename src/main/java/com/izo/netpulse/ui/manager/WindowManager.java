package com.izo.netpulse.ui.manager;

import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class WindowManager {
    private double xOffset = 0, yOffset = 0;
    private double prevX, prevY, prevWidth, prevHeight;
    private boolean isMaximized = false;

    private static final String MAXIMIZE_SVG = "M1,1 H9 V9 H1 V1 Z M2,2 V8 H8 V2 H2 Z";
    private static final String RESTORE_SVG = "M2,4 H8 V10 H2 V4 Z M3,5 V9 H7 V5 H3 Z M4,1 H10 V7 H9 V2 H4 V1 Z";

    public void handlePressed(MouseEvent e) { if (!isMaximized) { xOffset = e.getSceneX(); yOffset = e.getSceneY(); } }

    public void handleDragged(MouseEvent e, Stage stage) {
        if (stage != null && !isMaximized) {
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        }
    }

    public void toggleMaximize(Stage stage, SVGPath icon) {
        if (!isMaximized) {
            prevX = stage.getX(); prevY = stage.getY();
            prevWidth = stage.getWidth(); prevHeight = stage.getHeight();
            Rectangle2D b = Screen.getPrimary().getVisualBounds();
            stage.setX(b.getMinX()); stage.setY(b.getMinY());
            stage.setWidth(b.getWidth()); stage.setHeight(b.getHeight());
            icon.setContent(RESTORE_SVG);
        } else {
            stage.setX(prevX); stage.setY(prevY);
            stage.setWidth(prevWidth); stage.setHeight(prevHeight);
            icon.setContent(MAXIMIZE_SVG);
        }
        isMaximized = !isMaximized;
    }
}
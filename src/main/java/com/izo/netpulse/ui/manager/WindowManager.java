package com.izo.netpulse.ui.manager;

import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Manages custom window behavior for undecorated stages.
 * This class provides logic for dragging the window across the screen,
 * as well as toggling between maximized and restored states using
 * system screen bounds.
 */
public class WindowManager {
    private double xOffset = 0, yOffset = 0;
    private double prevX, prevY, prevWidth, prevHeight;
    private boolean isMaximized = false;

    /** SVG path data for the square 'Maximize' icon. */
    private static final String MAXIMIZE_SVG = "M2,2 H8 V8 H2 Z";

    /** SVG path data for the overlapping squares 'Restore' icon. */
    private static final String RESTORE_SVG = "M4,4 V2 H9 V7 M2,4 H7 V9 H2 Z";

    /**
     * Captures the initial mouse coordinates relative to the scene.
     * These coordinates are used as an offset to prevent the window
     * from "snapping" its top-left corner to the cursor during a drag.
     * @param e The mouse event triggered by a press on the window header.
     */
    public void handlePressed(MouseEvent e) {
        if (!isMaximized) {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        }
    }

    /**
     * Updates the stage position based on the current mouse location.
     * This logic is only executed if the window is not currently maximized.
     * @param e     The mouse event triggered by dragging.
     * @param stage The primary application stage to move.
     */
    public void handleDragged(MouseEvent e, Stage stage) {
        if (stage != null && !isMaximized) {
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        }
    }

    /**
     * Toggles the window between the primary screen's visual bounds and its
     * previous dimensions. It also updates the SVG icon to reflect the current state.
     * @param stage The stage to maximize or restore.
     * @param icon  The SVGPath element representing the maximize/restore button.
     */
    public void toggleMaximize(Stage stage, SVGPath icon) {
        if (!isMaximized) {
            // Save current bounds before maximizing
            prevX = stage.getX();
            prevY = stage.getY();
            prevWidth = stage.getWidth();
            prevHeight = stage.getHeight();

            // Apply visual bounds (excludes taskbar/dock)
            Rectangle2D b = Screen.getPrimary().getVisualBounds();
            stage.setX(b.getMinX());
            stage.setY(b.getMinY());
            stage.setWidth(b.getWidth());
            stage.setHeight(b.getHeight());

            icon.setContent(RESTORE_SVG);
        } else {
            // Restore previous bounds
            stage.setX(prevX);
            stage.setY(prevY);
            stage.setWidth(prevWidth);
            stage.setHeight(prevHeight);

            icon.setContent(MAXIMIZE_SVG);
        }
        isMaximized = !isMaximized;
    }
}
package com.izo.netpulse.ui.util;

import javafx.animation.*;
import javafx.scene.Group;
import javafx.util.Duration;

public class AnimationUtility {
    public static void switchToMarkers(Group toShow, Group toHide) {
        FadeTransition outF = new FadeTransition(Duration.millis(400), toHide);
        outF.setToValue(0.0);
        ScaleTransition outS = new ScaleTransition(Duration.millis(400), toHide);
        outS.setToX(0.8); outS.setToY(0.8);

        toShow.setOpacity(0); toShow.setScaleX(0.8); toShow.setScaleY(0.8);
        FadeTransition inF = new FadeTransition(Duration.millis(400), toShow);
        inF.setToValue(1.0);
        ScaleTransition inS = new ScaleTransition(Duration.millis(400), toShow);
        inS.setToX(1.0); inS.setToY(1.0);

        new ParallelTransition(outF, outS, inF, inS).play();
    }
}
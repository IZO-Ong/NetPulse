package com.izo.netpulse;

import javafx.stage.Stage;
import org.springframework.context.ApplicationEvent;

/**
 * Application event used to signal that the JavaFX Stage is initialized and ready.
 * This event acts as a bridge, allowing Spring-managed components to interact with
 * the UI container once it is available.
 */
public class StageReadyEvent extends ApplicationEvent {

    /**
     * Constructs a new StageReadyEvent.
     * @param stage The primary JavaFX stage created by the runtime.
     */
    public StageReadyEvent(Stage stage) {
        super(stage);
    }

    /**
     * Retrieves the primary stage associated with this event.
     * @return The {@link Stage} instance used as the event source.
     */
    public Stage getStage() {
        return (Stage) getSource();
    }
}
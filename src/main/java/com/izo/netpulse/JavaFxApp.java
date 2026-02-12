package com.izo.netpulse;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Entry point for the JavaFX application integrated with Spring Boot.
 * Manages the lifecycle of the Spring application context alongside the JavaFX toolkit.
 */
public class JavaFxApp extends Application {

    /**
     * The Spring application context used for dependency injection throughout the app.
     */
    private ConfigurableApplicationContext context;

    /**
     * Initializes the Spring Boot application context before the UI starts.
     * Converts JavaFX command-line parameters into Spring-compatible arguments.
     */
    @Override
    public void init() {
        this.context = new SpringApplicationBuilder()
                .sources(NetpulseApplication.class)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    /**
     * Sets up the primary stage and broadcasts a {@link StageReadyEvent}.
     * Allows Spring-managed listeners to handle UI loading and FXML injection.
     *
     * @param primaryStage The root stage provided by the JavaFX runtime.
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        context.publishEvent(new StageReadyEvent(primaryStage));
    }

    /**
     * Performs a shutdown of both the Spring context and the JavaFX platform.
     * Ensures database connections and background tasks are terminated properly.
     */
    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}
package com.izo.netpulse.ui;

import com.izo.netpulse.StageReadyEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

/**
 * Component responsible for initializing the primary JavaFX Stage.
 * It listens for the {@link StageReadyEvent}, which is typically published
 * when the Spring Boot context is fully refreshed and the JavaFX application
 * thread is ready to display the UI.
 */
@Component
public class PrimaryStageInitializer implements ApplicationListener<StageReadyEvent> {

    private final ApplicationContext context;

    /**
     * Constructs the initializer with the Spring ApplicationContext.
     * @param context The Spring application context used to wire controllers.
     */
    public PrimaryStageInitializer(ApplicationContext context) {
        this.context = context;
    }

    /**
     * Responds to the {@link StageReadyEvent} by loading the primary FXML layout,
     * setting up the scene, and displaying the stage.
     * @param event The event containing the primary JavaFX Stage.
     * @throws RuntimeException If the FXML resource cannot be located or loaded.
     */
    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        try {
            Stage stage = event.getStage();

            // Set the application icon
            stage.getIcons().add(new javafx.scene.image.Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/style/icon.png"))
            ));

            stage.setTitle("NetPulse");

            java.net.URL fxmlUrl = getClass().getResource("/fxml/netpulse.fxml");

            if (fxmlUrl == null) {
                throw new RuntimeException("Could not find fxml/netpulse.fxml. Check your resources folder.");
            }

            FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);

            /* Tells JavaFX to use the Spring ApplicationContext
             * as a factory for controllers. This allows @Autowired and @RequiredArgsConstructor
             * to work inside JavaFX controller classes.
             */
            fxmlLoader.setControllerFactory(context::getBean);

            Parent root = fxmlLoader.load();

            // Initialize the scene with the loaded root and apply to the stage
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            throw new RuntimeException("Failed to load netpulse.fxml", e);
        }
    }
}
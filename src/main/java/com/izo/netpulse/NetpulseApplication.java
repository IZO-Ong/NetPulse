package com.izo.netpulse;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The main entry point for the NetPulse application.
 * This class initializes the Spring Boot context and delegates the application
 * lifecycle to {@link JavaFxApp}.
 */
@SpringBootApplication
@EnableScheduling
public class NetpulseApplication {

    /**
     * Main method that launches the JavaFX platform.
     * Note: Standard SpringBootApplication.run() is called within the
     * JavaFX init lifecycle in {@link JavaFxApp#init()}.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(String[] args) {
        Application.launch(JavaFxApp.class, args);
    }
}
package com.izo.netpulse;

import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NetpulseApplication {

	public static void main(String[] args) {
        Application.launch(JavaFxApp.class, args);
	}

}

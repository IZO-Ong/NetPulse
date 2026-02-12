package com.izo.netpulse.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Represents the results of a single network speed test execution.
 * This entity persists download and upload metrics along with a temporal record.
 */
@Entity
@Data
public class SpeedTestResult {

    /**
     * The unique identifier for the speed test record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The measured download speed in Megabits per second (Mbps).
     */
    private double downloadMbps;

    /**
     * The measured upload speed in Megabits per second (Mbps).
     */
    private double uploadMbps;

    /**
     * The date and time when the speed test was performed.
     */
    private LocalDateTime timestamp;

}
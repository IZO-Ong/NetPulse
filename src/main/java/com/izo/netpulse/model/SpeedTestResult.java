package com.izo.netpulse.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class SpeedTestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double downloadMbps;
    private double uploadMbps;
    private LocalDateTime timestamp;

}
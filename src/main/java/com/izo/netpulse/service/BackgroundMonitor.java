package com.izo.netpulse.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BackgroundMonitor {
    private final SpeedTestService speedTestService;

    @Scheduled(fixedRate = 900000)
    public void autoPulse() {
        speedTestService.runTest();
    }
}
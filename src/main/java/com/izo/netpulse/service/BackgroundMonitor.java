package com.izo.netpulse.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackgroundMonitor {

    private final SpeedTestService speedTestService;

    // Runs every 15 minutes (900,000 ms)
    @Scheduled(fixedRate = 900000)
    public void autoPulse() {
        log.info("Starting scheduled background speed pulse...");

        // Use the new 7-second sampling test
        speedTestService.runPulseTest(new SpeedTestService.PulseCallback() {
            @Override
            public void onInstantUpdate(double mbps) {
                // Background task: No UI to update, so we do nothing here
            }

            @Override
            public void onComplete(double averageMbps) {
                // The service already handles saving to the DB in runPulseTest,
                // so we just log the completion here.
                log.info("Scheduled pulse complete. Average Download: {} Mbps",
                        String.format("%.2f", averageMbps));
            }
        });
    }
}
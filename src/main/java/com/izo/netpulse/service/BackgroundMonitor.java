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

    // Runs every 15 minutes (900000 ms)
    @Scheduled(fixedRate = 900000)
    public void autoPulse() {
        log.info("Starting scheduled background speed pulse...");

        speedTestService.runDownloadTest(new SpeedTestService.PulseCallback() {
            @Override
            public void onInstantUpdate(double mbps) {
            }

            @Override
            public void onComplete(double avgDownload) {
                log.info("Background Download Phase Complete: {} Mbps. Starting Upload...",
                        String.format("%.2f", avgDownload));

                speedTestService.runUploadTest(new SpeedTestService.PulseCallback() {
                    @Override
                    public void onInstantUpdate(double mbps) {}

                    @Override
                    public void onComplete(double avgUpload) {
                        speedTestService.saveResult(avgDownload, avgUpload);

                        log.info("Scheduled pulse complete. DL: {} Mbps | UL: {} Mbps",
                                String.format("%.2f", avgDownload),
                                String.format("%.2f", avgUpload));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        log.error("Background Upload Pulse failed: {}", errorMessage);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                log.error("Background Download Pulse failed: {}", errorMessage);
            }
        });
    }
}
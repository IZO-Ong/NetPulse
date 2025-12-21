package com.izo.netpulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundMonitorService {

    private final SpeedTestService speedTestService;
    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledTask;

    public void startOrUpdateMonitoring(int minutes, Runnable onCompleteCallback) {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            log.info("Adjusting background interval to {} minutes...", minutes);
            scheduledTask.cancel(false);
        } else {
            log.info("Background monitoring enabled ({} min interval).", minutes);
        }

        Duration interval = Duration.ofMinutes(minutes);
        Instant firstStartTime = Instant.now().plus(interval);

        scheduledTask = taskScheduler.scheduleAtFixedRate(
                () -> runBackgroundTest(onCompleteCallback),
                firstStartTime,
                interval
        );
    }

    public void stopMonitoring() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            log.info("Background monitoring disabled by user.");
        }
    }

    private void runBackgroundTest(Runnable onCompleteCallback) {
        log.info("Starting background test...");
        speedTestService.runDownloadTest(new SpeedTestService.TestCallback() {
            @Override public void onInstantUpdate(double mbps) {}
            @Override
            public void onComplete(double avgDl) {
                speedTestService.runUploadTest(new SpeedTestService.TestCallback() {
                    @Override public void onInstantUpdate(double mbps) {}
                    @Override
                    public void onComplete(double avgUl) {
                        speedTestService.saveResult(avgDl, avgUl);
                        log.info("Background test complete. DL: {} Mbps | UL: {} Mbps", avgDl, avgUl);

                        if (onCompleteCallback != null) {
                            onCompleteCallback.run();
                        }
                    }
                    @Override public void onError(String msg) { log.error("BG Upload Error: {}", msg); }
                });
            }
            @Override public void onError(String msg) { log.error("BG Download Error: {}", msg); }
        });
    }
}
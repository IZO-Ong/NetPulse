package com.izo.netpulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * Service responsible for managing scheduled network monitoring tasks.
 * Handles starting, stopping, and re-scheduling background speed tests
 * using a {@link TaskScheduler}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundMonitorService {

    private final SpeedTestService speedTestService;
    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledTask;

    /**
     * Starts background monitoring at a fixed interval or updates the interval if already running.
     * The first test is scheduled to run after one interval period has elapsed from now.
     *
     * @param minutes            The interval between tests in minutes.
     * @param onCompleteCallback A callback to execute after each successful test sequence.
     */
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

    /**
     * Cancels the currently scheduled background monitoring task.
     * If no task is running, this method has no effect.
     */
    public void stopMonitoring() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            log.info("Background monitoring disabled by user.");
        }
    }

    /**
     * Executes the background test sequence, performing a download test followed by an upload test.
     * Results are automatically persisted to the database upon completion.
     *
     * @param onCompleteCallback The callback to notify upon successful completion.
     */
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
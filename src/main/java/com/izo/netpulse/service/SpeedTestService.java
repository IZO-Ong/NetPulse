package com.izo.netpulse.service;

import com.izo.netpulse.model.SpeedTestResult;
import com.izo.netpulse.repository.SpeedRepository;
import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import javafx.application.Platform;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Core service for executing network speed tests.
 * Manages asynchronous download and upload tasks, latency measurements,
 * and persistence of test results.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeedTestService {

    private final SpeedRepository repository;
    private final OkHttpClient okClient = new OkHttpClient();
    private SpeedTestSocket speedTestSocket;
    private Call activeOkCall;

    private volatile boolean isCancelled = false;
    private long lastUpdateTick = 0;

    /**
     * The average latency (ping) calculated during the most recent measurement.
     */
    @Getter
    private volatile double currentLatency = 0.0;

    /**
     * Callback interface for communicating test progress and results to the UI.
     */
    public interface TestCallback {
        void onInstantUpdate(double mbps);
        void onComplete(double averageMbps);
        void onError(String msg);
    }

    /**
     * Initiates an asynchronous download test using a bot-friendly mirror.
     * Uses a sliding window moving average for smooth UI updates.
     *
     * @param callback The handler for real-time updates and final results.
     */
    public void runDownloadTest(TestCallback callback) {
        isCancelled = false;

        String url = "https://download.thinkbroadband.com/1GB.zip";

        activeOkCall = okClient.newCall(new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build());

        new Thread(() -> {
            long testStartTime = System.currentTimeMillis();
            List<Double> speedSamples = new ArrayList<>();
            Queue<Double> window = new LinkedList<>();
            int windowSize = 10;

            try (Response response = activeOkCall.execute()) {
                if (!response.isSuccessful()) {
                    log.error("Download failed with HTTP {}: Check if the mirror is reachable.", response.code());
                    throw new IOException("HTTP " + response.code());
                }

                InputStream is = response.body().byteStream();
                byte[] buffer = new byte[32768];
                long lastTick = System.currentTimeMillis();
                long bytesInInterval = 0;
                int read;

                while (!isCancelled && (read = is.read(buffer)) != -1) {
                    long now = System.currentTimeMillis();
                    bytesInInterval += read;

                    if (now - lastTick >= 200) {
                        double seconds = (now - lastTick) / 1000.0;
                        double mbps = (bytesInInterval * 8.0) / (1_000_000.0 * seconds);

                        speedSamples.add(mbps);
                        window.add(mbps);
                        if (window.size() > windowSize) window.poll();

                        double movingAvg = window.stream().mapToDouble(d -> d).average().orElse(0.0);
                        Platform.runLater(() -> callback.onInstantUpdate(movingAvg));

                        bytesInInterval = 0;
                        lastTick = now;
                    }

                    if (now - testStartTime >= 7000) break;
                }

                if (!isCancelled) {
                    double avg = speedSamples.stream().mapToDouble(d -> d).average().orElse(0.0);
                    Platform.runLater(() -> callback.onComplete(avg));
                }

            } catch (java.net.SocketTimeoutException e) {
                log.error("Download timed out.");
                if (!isCancelled) handleError(callback, "Connection Timeout");
            } catch (java.net.UnknownHostException e) {
                log.error("DNS Resolution failed.");
                if (!isCancelled) handleError(callback, "DNS Error: Host Unreachable");
            } catch (javax.net.ssl.SSLHandshakeException | javax.net.ssl.SSLPeerUnverifiedException e) {
                log.error("SSL/Security Error: {}", e.getMessage());
                if (!isCancelled) handleError(callback, "Security Error (SSL)");
            } catch (Exception e) {
                if (!isCancelled) {
                    log.error("Download failed due to unexpected error: {}", e.getMessage(), e);
                    handleError(callback, "Download Failed: " + e.getClass().getSimpleName());
                }
            }
        }).start();
    }

    /**
     * Helper to ensure the UI is notified on the correct thread.
     */
    private void handleError(TestCallback callback, String msg) {
        Platform.runLater(() -> callback.onError(msg));
    }

    /**
     * Measures average network latency by performing a series of HTTP HEAD requests.
     *
     * @param onComplete Runnable executed upon successful measurement.
     * @param onError    Runnable executed if all ping attempts fail.
     */
    public void measureLatencyAverage(Runnable onComplete, Runnable onError) {
        new Thread(() -> {
            List<Double> latencies = new ArrayList<>();
            Request pingRequest = new Request.Builder()
                    .url("https://www.google.com/generate_204")
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build();

            try {
                for (int i = 0; i < 5; i++) {
                    if (isCancelled) return;
                    long start = System.currentTimeMillis();
                    try (Response response = okClient.newCall(pingRequest).execute()) {
                        if (response.isSuccessful()) {
                            latencies.add((double)(System.currentTimeMillis() - start));
                        }
                    } catch (IOException ignored) {}
                }

                if (latencies.isEmpty()) throw new IOException("No pings succeeded");

                this.currentLatency = latencies.stream().mapToDouble(d -> d).average().orElse(0.0);
                Platform.runLater(onComplete);
            } catch (Exception e) {
                log.error("Latency check failed", e);
                Platform.runLater(onError);
            }
        }).start();
    }

    /**
     * Initiates an asynchronous upload test with a watchdog timer.
     * Includes a manual warm-up period to ignore initial OS buffering spikes.
     *
     * @param callback The handler for real-time updates and final results.
     */
    public void runUploadTest(TestCallback callback) {
        isCancelled = false;
        String url = "https://speedtest.newark.linode.com/empty.php";

        List<Double> uploadSamples = new ArrayList<>();
        Queue<Double> window = new LinkedList<>();
        int windowSize = 10;

        // Track when the test actually starts
        final long testStartTime = System.currentTimeMillis();
        lastUpdateTick = testStartTime;

        speedTestSocket = new SpeedTestSocket();
        speedTestSocket.setUploadSetupTime(1000);

        speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onProgress(float percent, SpeedTestReport report) {
                long now = System.currentTimeMillis();

                if (!isCancelled && (now - lastUpdateTick >= 200) && (now - testStartTime > 1500)) {
                    double mbps = report.getTransferRateBit().doubleValue() / 1_000_000.0;

                    uploadSamples.add(mbps);
                    window.add(mbps);
                    if (window.size() > windowSize) window.poll();

                    double movingAvg = window.stream().mapToDouble(d -> d).average().orElse(0.0);

                    Platform.runLater(() -> callback.onInstantUpdate(movingAvg));
                    lastUpdateTick = now;
                }
            }

            @Override
            public void onCompletion(SpeedTestReport report) {
                if (!isCancelled) {
                    double avg = uploadSamples.stream().mapToDouble(d -> d).average().orElse(0.0);
                    Platform.runLater(() -> callback.onComplete(avg));
                    isCancelled = true;
                }
            }

            @Override
            public void onError(SpeedTestError error, String msg) {
                if (!isCancelled) {
                    log.error("Upload failed: Type={} | Msg={}", error, msg);
                    Platform.runLater(() -> callback.onError("Upload Failed: " + error.name()));
                    isCancelled = true;
                }
            }
        });

        new Thread(() -> {
            try {
                speedTestSocket.startFixedUpload(url, 50_000_000, 7000);

                // Watchdog grace period
                Thread.sleep(8500);

                if (!isCancelled) {
                    log.warn("Upload Watchdog: Test hung. Forcing completion.");
                    stopTest();
                    double avg = uploadSamples.stream().mapToDouble(d -> d).average().orElse(0.0);
                    Platform.runLater(() -> callback.onComplete(avg));
                    isCancelled = true;
                }
            } catch (Exception e) {
                log.error("Upload thread error", e);
            }
        }).start();
    }

    /**
     * Forces all active network calls and sockets to stop immediately.
     * Sets the cancellation flag to prevent further UI updates.
     */
    public void stopTest() {
        isCancelled = true;
        if (activeOkCall != null) activeOkCall.cancel();
        if (speedTestSocket != null) speedTestSocket.forceStopTask();
    }

    /**
     * Persists the results of a completed speed test to the database.
     *
     * @param dl Final average download speed in Mbps.
     * @param ul Final average upload speed in Mbps.
     */
    public void saveResult(double dl, double ul) {
        SpeedTestResult result = new SpeedTestResult();
        result.setDownloadMbps(dl);
        result.setUploadMbps(ul);
        result.setTimestamp(LocalDateTime.now());
        repository.save(result);
    }
}
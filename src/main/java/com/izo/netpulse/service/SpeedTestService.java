package com.izo.netpulse.service;

import com.izo.netpulse.model.SpeedTestResult;
import com.izo.netpulse.repository.SpeedRepository;
import fr.bmartel.speedtest.SpeedTestSocket;
import javafx.application.Platform;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core service for executing network speed tests.
 * Manages asynchronous download and upload tasks, latency measurements,
 * and persistence of test results to the database.
 * <p>This service utilizes a mix of OkHttp for downloads and the native
 * {@link java.net.http.HttpClient} for uploads to bypass module visibility
 * issues common with the okio library in named modules.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeedTestService {

    private final SpeedRepository repository;
    private final OkHttpClient okClient = new OkHttpClient();
    private SpeedTestSocket speedTestSocket;
    private Call activeOkCall;

    /**
     * Flag used to immediately terminate active network streams and UI updates.
     */
    private volatile boolean isCancelled = false;

    /**
     * The average latency (ping) in milliseconds calculated during the most recent measurement.
     * @return The current measured latency.
     */
    @Getter
    private volatile double currentLatency = 0.0;

    /**
     * Callback interface for communicating test progress and final results to the UI.
     * Updates are guaranteed to be wrapped in {@link Platform#runLater(Runnable)}.
     */
    public interface TestCallback {
        /**
         * Called periodically to update the real-time speed display (e.g., a gauge needle).
         * @param mbps The instantaneous speed in Megabits per second.
         */
        void onInstantUpdate(double mbps);

        /**
         * Called when the test completes successfully.
         * @param averageMbps The calculated average speed for the duration of the test.
         */
        void onComplete(double averageMbps);

        /**
         * Called if the test fails due to network or logic errors.
         * @param msg Descriptive error message.
         */
        void onError(String msg);
    }

    /**
     * Initiates an asynchronous download test using OkHttp.
     * <p>The test requests 1GB of data from Cloudflare but terminates after 7 seconds
     * or upon cancellation to provide a consistent user experience.</p>
     * @param callback The handler for real-time updates and results.
     */
    public void runDownloadTest(TestCallback callback) {
        isCancelled = false;
        String url = "https://speed.cloudflare.com/__down?bytes=1000000000";

        activeOkCall = okClient.newCall(new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0")
                .addHeader("Accept", "*/*")
                .addHeader("Referer", "https://speed.cloudflare.com/")
                .addHeader("Sec-Fetch-Mode", "cors")
                .addHeader("Sec-Fetch-Site", "same-origin")
                .build());

        new Thread(() -> {
            long testStartTime = System.currentTimeMillis();
            List<Double> speedSamples = new ArrayList<>();
            Queue<Double> window = new LinkedList<>();

            try (Response response = activeOkCall.execute()) {
                if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());

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
                        if (window.size() > 10) window.poll();

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
            } catch (Exception e) {
                if (!isCancelled) handleError(callback, "Download Failed: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Initiates an asynchronous upload test using the native {@link HttpClient}.
     * <p>This implementation avoids third-party okio dependencies to prevent module visibility errors.
     * It uses a custom {@link FilterInputStream} to track progress and enforce a 7-second cutoff.</p>
     * * @param callback The handler for real-time updates and results.
     */
    public void runUploadTest(TestCallback callback) {
        isCancelled = false;
        String url = "https://speed.cloudflare.com/__up";
        byte[] data = new byte[500_000_000]; // 500MB payload

        AtomicLong totalBytesSent = new AtomicLong(0);
        long testStartTime = System.currentTimeMillis();

        InputStream progressStream = new FilterInputStream(new ByteArrayInputStream(data)) {
            private long lastTick = System.currentTimeMillis();
            private long bytesInInterval = 0;

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                long now = System.currentTimeMillis();

                // Terminate if 7 seconds elapsed or if the user cancelled
                if (isCancelled || (now - testStartTime >= 7000)) {
                    return -1;
                }

                int read = super.read(b, off, len);
                if (read != -1) {
                    bytesInInterval += read;
                    totalBytesSent.addAndGet(read);

                    if (now - lastTick >= 150) { // Trigger needle movement every 150ms
                        double seconds = (now - lastTick) / 1000.0;
                        double mbps = (bytesInInterval * 8.0) / (1_000_000.0 * seconds);
                        Platform.runLater(() -> callback.onInstantUpdate(mbps));
                        bytesInInterval = 0;
                        lastTick = now;
                    }
                }
                return read;
            }
        };

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0")
                .header("Referer", "https://speed.cloudflare.com/")
                .header("Origin", "https://speed.cloudflare.com")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> progressStream))
                .build();

        new Thread(() -> {
            try {
                // Execute the POST; the progressStream handles the 7s termination
                client.send(request, HttpResponse.BodyHandlers.discarding());

                if (!isCancelled) {
                    long endTime = System.currentTimeMillis();
                    double totalSeconds = (endTime - testStartTime) / 1000.0;
                    // Mbps = (total_bits) / (1,000,000 * seconds)
                    double finalMbps = (totalBytesSent.get() * 8.0) / (1_000_000.0 * totalSeconds);
                    Platform.runLater(() -> callback.onComplete(finalMbps));
                }
            } catch (Exception e) {
                if (!isCancelled) handleError(callback, "Upload Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Measures average network latency by performing 5 consecutive HTTP HEAD requests.
     * Result is stored in {@link #currentLatency}.
     * @param onComplete Runnable to execute on the UI thread upon success.
     * @param onError    Runnable to execute on the UI thread if pings fail.
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
                if (latencies.isEmpty()) throw new IOException("Ping failed");
                this.currentLatency = latencies.stream().mapToDouble(d -> d).average().orElse(0.0);
                Platform.runLater(onComplete);
            } catch (Exception e) {
                Platform.runLater(onError);
            }
        }).start();
    }

    /**
     * Helper method to dispatch error messages to the UI thread.
     */
    private void handleError(TestCallback callback, String msg) {
        Platform.runLater(() -> callback.onError(msg));
    }

    /**
     * Forces all active network calls and sockets to stop immediately.
     * Resets internal flags to prevent further data sampling or UI updates.
     */
    public void stopTest() {
        isCancelled = true;
        if (activeOkCall != null) activeOkCall.cancel();
        if (speedTestSocket != null) speedTestSocket.forceStopTask();
    }

    /**
     * Persists the results of a completed speed test session to the local database.
     * @param dl The final average download speed in Megabits per second.
     * @param ul The final average upload speed in Megabits per second.
     */
    public void saveResult(double dl, double ul) {
        SpeedTestResult result = new SpeedTestResult();
        result.setDownloadMbps(dl);
        result.setUploadMbps(ul);
        result.setTimestamp(LocalDateTime.now());
        repository.save(result);
    }
}
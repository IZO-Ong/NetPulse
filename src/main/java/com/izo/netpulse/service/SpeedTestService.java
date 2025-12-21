package com.izo.netpulse.service;

import com.izo.netpulse.model.SpeedTestResult;
import com.izo.netpulse.repository.SpeedRepository;
import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeedTestService {

    private final SpeedRepository repository;
    private final OkHttpClient okClient = new OkHttpClient();
    private SpeedTestSocket speedTestSocket;
    private Call activeOkCall;

    private volatile boolean isCancelled = false;
    private long lastUpdateTick = 0;

    public interface PulseCallback {
        void onInstantUpdate(double mbps);
        void onComplete(double averageMbps);
        void onError(String msg);
    }

    // Download phase with Okhttp
    public void runDownloadTest(PulseCallback callback) {
        isCancelled = false;
        String url = "https://speed.cloudflare.com/__down?bytes=150000000";
        activeOkCall = okClient.newCall(new Request.Builder().url(url).build());

        new Thread(() -> {
            long testStartTime = System.currentTimeMillis();
            List<Double> speedSamples = new ArrayList<>();
            try (Response response = activeOkCall.execute()) {
                if (!response.isSuccessful())
                    throw new IOException("HTTP " + response.code());

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
                        Platform.runLater(() -> callback.onInstantUpdate(mbps));
                        bytesInInterval = 0;
                        lastTick = now;
                    }
                    if (now - testStartTime >= 7000) break;
                }
                double avg = speedSamples.stream().mapToDouble(d -> d).average().orElse(0.0);
                Platform.runLater(() -> callback.onComplete(avg));
            } catch (IOException e) {
                if (!isCancelled) Platform.runLater(() -> callback.onError("DL Error: " + e.getMessage()));
            }
        }).start();
    }

    // Upload phase with JSpeedTest
    public void runUploadTest(PulseCallback callback) {
        isCancelled = false;
        String url = "https://speed.cloudflare.com/__up";
        List<Double> uploadSamples = new ArrayList<>();
        lastUpdateTick = System.currentTimeMillis();

        speedTestSocket = new SpeedTestSocket();
        speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onProgress(float percent, SpeedTestReport report) {
                long now = System.currentTimeMillis();
                if (now - lastUpdateTick >= 200 && !isCancelled) {
                    double mbps = report.getTransferRateBit().doubleValue() / 1_000_000.0;
                    uploadSamples.add(mbps);
                    Platform.runLater(() -> callback.onInstantUpdate(mbps));
                    lastUpdateTick = now;
                }
            }

            @Override
            public void onCompletion(SpeedTestReport report) {
                double avg = uploadSamples.stream().mapToDouble(d -> d).average().orElse(0.0);
                Platform.runLater(() -> callback.onComplete(avg));
            }

            @Override
            public void onError(SpeedTestError error, String msg) {
                if (!isCancelled) {
                    if (!uploadSamples.isEmpty()) {
                        onCompletion(null);
                    } else {
                        Platform.runLater(() -> callback.onError("UL Error: " + msg));
                    }
                }
            }
        });

        new Thread(() -> {
            // 100MB dummy payload
            speedTestSocket.startFixedUpload(url, 100_000_000, 7000);
        }).start();
    }

    public void stopTest() {
        isCancelled = true;
        if (activeOkCall != null) activeOkCall.cancel();
        if (speedTestSocket != null) speedTestSocket.forceStopTask();
    }

    public void saveResult(double dl, double ul) {
        SpeedTestResult result = new SpeedTestResult();
        result.setDownloadMbps(dl);
        result.setUploadMbps(ul);
        result.setTimestamp(LocalDateTime.now());
        repository.save(result);
    }
}
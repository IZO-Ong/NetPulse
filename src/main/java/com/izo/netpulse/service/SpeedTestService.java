package com.izo.netpulse.service;

import com.izo.netpulse.model.SpeedTestResult;
import com.izo.netpulse.repository.SpeedRepository;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import javafx.application.Platform;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class SpeedTestService {

    private final SpeedRepository repository;
    private final OkHttpClient client = new OkHttpClient();

    public interface PulseCallback {
        void onInstantUpdate(double mbps);
        void onComplete(double averageMbps);
    }

    public void runPulseTest(PulseCallback callback) {
        String testUrl = "https://speed.cloudflare.com/__down?bytes=200000000";
        Request request = new Request.Builder().url(testUrl).build();

        new Thread(() -> {
            long testStartTime = System.currentTimeMillis();
            long durationLimit = 7000; // 7 seconds
            List<Double> speedSamples = new ArrayList<>();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return;
                InputStream is = response.body().byteStream();
                byte[] buffer = new byte[16384];

                long intervalStartTime = System.currentTimeMillis();
                long bytesInInterval = 0;

                int read;
                while ((read = is.read(buffer)) != -1) {
                    long now = System.currentTimeMillis();
                    bytesInInterval += read;

                    if (now - intervalStartTime >= 200) {
                        double intervalSeconds = (now - intervalStartTime) / 1000.0;
                        double instantMbps = (bytesInInterval * 8.0) / (1_000_000.0 * intervalSeconds);

                        speedSamples.add(instantMbps);
                        Platform.runLater(() -> callback.onInstantUpdate(instantMbps));

                        bytesInInterval = 0;
                        intervalStartTime = now;
                    }

                    if (now - testStartTime >= durationLimit) break;
                }

                double averageMbps = speedSamples.stream().mapToDouble(d -> d).average().orElse(0.0);

                // Save to DB and update final UI
                SpeedTestResult result = new SpeedTestResult();
                result.setDownloadMbps(averageMbps);
                result.setTimestamp(LocalDateTime.now());
                repository.save(result);

                Platform.runLater(() -> callback.onComplete(averageMbps));

            } catch (IOException e) {
                System.err.println("Download failed: " + e.getMessage());
            }
        }).start();
    }
}
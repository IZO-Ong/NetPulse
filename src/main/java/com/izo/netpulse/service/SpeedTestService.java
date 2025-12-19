package com.izo.netpulse.service;

import com.izo.netpulse.model.SpeedTestResult;
import com.izo.netpulse.repository.SpeedRepository;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SpeedTestService {

    private final SpeedRepository repository;
    private final OkHttpClient client = new OkHttpClient();

    public SpeedTestResult runTest() {
        double downloadMbps = performDownloadTest();
        
        SpeedTestResult result = new SpeedTestResult();
        result.setDownloadMbps(downloadMbps);
        result.setUploadMbps(0.0);
        result.setTimestamp(LocalDateTime.now());
        
        return repository.save(result);
    }

    private double performDownloadTest() {
        // 10Mb test from Cloudflare
        String testUrl = "https://speed.cloudflare.com/__down?bytes=10000000";
        Request request = new Request.Builder().url(testUrl).build();
        
        long startTime = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return 0.0;
            
            byte[] data = response.body().bytes();
            long endTime = System.currentTimeMillis();
            
            double durationSeconds = (endTime - startTime) / 1000.0;
            double megabits = (data.length * 8.0) / 1_000_000.0;
            
            return megabits / durationSeconds;
        } catch (IOException e) {
            System.err.println("Download test failed: " + e.getMessage());
            return 0.0;
        }
    }
}
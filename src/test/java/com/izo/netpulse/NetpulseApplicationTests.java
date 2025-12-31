package com.izo.netpulse;

import com.izo.netpulse.model.SpeedTestResult;
import com.izo.netpulse.repository.SpeedRepository;
import com.izo.netpulse.service.SpeedFeedbackService;
import com.izo.netpulse.service.SpeedTestService;
import com.izo.netpulse.service.DiagnosticService;
import com.izo.netpulse.ui.NetPulseController;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
// cleans Spring context and H2 database between test runs if needed
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class NetpulseApplicationTests {

    @Autowired
    private NetPulseController controller;

    @Autowired
    private SpeedFeedbackService feedbackService;

    @Autowired
    private SpeedRepository repository;

    @Autowired
    private SpeedTestService speedTestService;

    @Autowired
    private DiagnosticService diagnosticService;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setup() throws IOException {
        repository.deleteAll();
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Context should load and inject Controller")
    void contextLoads() {
        assertNotNull(controller, "Spring should inject the NetPulseController");
    }

    @Test
    @DisplayName("Validates descriptive string generation")
    void testSpeedFeedbackLogic() {
        String feedback = feedbackService.getFeedback(150.0);
        assertNotNull(feedback);
        assertTrue(feedback.length() > 10, "Feedback should be descriptive");
    }

    @Test
    @DisplayName("Edge cases (0 and high speed)")
    void testSpeedFeedbackEdgeCases() {
        assertNotNull(feedbackService.getFeedback(0.0));
        assertNotNull(feedbackService.getFeedback(10000.0));
    }

    @Test
    @DisplayName("Save and retrieve single record")
    void testDatabaseOperations() {
        SpeedTestResult result = new SpeedTestResult();
        result.setDownloadMbps(100.5);
        result.setUploadMbps(50.2);
        result.setTimestamp(LocalDateTime.now());

        repository.save(result);
        assertEquals(1, repository.count(), "Database count should be exactly 1");

        SpeedTestResult fetched = repository.findAll().getFirst();
        assertEquals(100.5, fetched.getDownloadMbps());
    }

    @Test
    @DisplayName("Handle batch of 10 records")
    void testLargeDataPersistence() {
        for (int i = 1; i <= 10; i++) {
            SpeedTestResult res = new SpeedTestResult();
            res.setDownloadMbps(i * 10.0);
            res.setUploadMbps(i * 5.0);
            res.setTimestamp(LocalDateTime.now());
            repository.save(res);
        }
        assertEquals(10, repository.count(), "Database should contain exactly 10 records");
    }

    @Test
    @DisplayName("Latency should start at 0.0")
    void testInitialLatencyValue() {
        assertEquals(0.0, speedTestService.getCurrentLatency(), "Ping should be 0 before tests run");
    }

    @Test
    @DisplayName("OkHttp: Verify service handles network error gracefully")
    void testNetworkErrorHandling() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));
        assertDoesNotThrow(() -> speedTestService.runDownloadTest(new SpeedTestService.TestCallback() {
            @Override public void onInstantUpdate(double mbps) {}
            @Override public void onComplete(double avg) {}
            @Override public void onError(String msg) {
                assertTrue(msg.contains("Error"), "Callback should report an error");
            }
        }));
    }

    @Test
    @DisplayName("Verify Preferences persistence")
    void testSettingsPersistence() {
        Preferences prefs = Preferences.userNodeForPackage(NetpulseApplicationTests.class);
        String testKey = "test_setting";

        prefs.putBoolean(testKey, true);
        assertTrue(prefs.getBoolean(testKey, false));

        prefs.remove(testKey); // Cleanup
    }

    @Test
    @DisplayName("Verify all core services injected")
    void testCoreInjections() {
        assertNotNull(repository);
        assertNotNull(feedbackService);
        assertNotNull(speedTestService);
    }

    @Test
    @DisplayName("Feedback edge cases")
    void testFeedbackEdgeCases() {
        assertNotNull(feedbackService.getFeedback(-1.0)); // Negative speed check
        assertNotNull(feedbackService.getFeedback(5000.0)); // Ultra-high speed check
    }

    @Test
    @DisplayName("Bulk save check")
    void testBulkSave() {
        for (int i = 0; i < 5; i++) {
            SpeedTestResult result = new SpeedTestResult();
            result.setDownloadMbps(i * 10.0);
            result.setUploadMbps(i * 5.0);
            result.setTimestamp(LocalDateTime.now());
            repository.save(result);
        }
        assertEquals(5, repository.count());
    }

    @Test
    @DisplayName("Verify DiagnosticService is injected")
    void testDiagnosticServiceInjection() {
        assertNotNull(diagnosticService, "DiagnosticService should be managed by Spring");
    }

    @Test
    @DisplayName("DNS detection should return a non-empty string")
    void testDnsDetection() {
        String dns = diagnosticService.getDnsServers();
        assertNotNull(dns, "DNS result should not be null");
        assertFalse(dns.isEmpty(), "DNS result should contain data");
    }

    @Test
    @DisplayName("Location API should return successful format or known error")
    void testLocationService() {
        String location = diagnosticService.getServerLocation();
        assertNotNull(location, "Location result should not be null");
        // Check if it contains the IP label which is part of our successful string format
        assertTrue(location.contains("IP:") || location.contains("Error"),
                "Location should return valid data or a handled error message");
    }

    @Test
    @DisplayName("First Hop (Gateway) should return valid local IP or timeout")
    void testFirstHopDetection() {
        String gateway = diagnosticService.getFirstHop();
        assertNotNull(gateway);
        // Gateway should usually contain the hop number "1"
        assertTrue(gateway.contains("1") || gateway.contains("timeout"),
                "Gateway should identify the first hop or report a timeout");
    }

    @Test
    @DisplayName("Web Reachability test should return connectivity status")
    void testWebReachability() {
        String status = diagnosticService.checkWebReachability();
        assertNotNull(status);
        assertTrue(status.contains("Accessible") || status.contains("Unreachable"),
                "Should report either success or failure for HTTP connectivity");
    }

    @Test
    @DisplayName("DNS Speed test should return a timed value in ms")
    void testDnsResolutionSpeed() {
        String speed = diagnosticService.testDnsSpeed("google.com");
        assertNotNull(speed);
        assertTrue(speed.contains("ms") || speed.contains("Failed"),
                "DNS resolution speed should be formatted in milliseconds");
    }
}
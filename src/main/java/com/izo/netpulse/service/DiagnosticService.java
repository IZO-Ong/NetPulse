package com.izo.netpulse.service;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DiagnosticService {

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    // Regex to match IPv4 addresses
    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    public String getServerLocation() {
        Request request = new Request.Builder()
                .url("http://ip-api.com/line/?fields=status,message,country,city,isp,as,query")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "API Error: " + response.code();

            String[] lines = response.body().string().split("\n");
            if (lines[0].equalsIgnoreCase("success")) {
                return String.format("%s, %s (%s) \nASN: %s \nPublic IP: %s",
                        lines[2], lines[1], lines[3], lines[4], lines[5]);
            } else {
                return "Location Error: " + lines[1];
            }
        } catch (Exception e) {
            return "Offline or API Unreachable";
        }
    }

    public String checkWebReachability() {
        Request request = new Request.Builder().url("http://google.com").head().build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful() ? "Accessible (HTTP 200)" : "Limited Connectivity";
        } catch (Exception e) {
            return "No Internet Connection";
        }
    }

    public String getDnsServers() {
        try {
            Process process = Runtime.getRuntime().exec("ipconfig /all");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder dnsInfo = new StringBuilder();
            boolean foundDns = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("DNS Servers")) {
                    foundDns = true;
                    dnsInfo.append(line.split(":")[1].trim()).append(" ");
                } else if (foundDns && line.trim().matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    dnsInfo.append(line.trim()).append(" ");
                } else if (foundDns && !line.trim().isEmpty() && !line.contains("DNS Servers")) {
                    break;
                }
            }
            return dnsInfo.toString().trim();
        } catch (Exception e) {
            return "Could not detect DNS";
        }
    }

    public String testDnsSpeed(String domain) {
        long start = System.currentTimeMillis();
        try {
            InetAddress.getByName(domain);
            return (System.currentTimeMillis() - start) + "ms";
        } catch (Exception e) {
            return "Resolution Failed (Offline)";
        }
    }

    public String getFirstHop() {
        return executeCommand(List.of("tracert", "-d", "-h", "1", "8.8.8.8"), (reader) -> {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("1 ")) {
                    return line.trim();
                }
            }
            return "First hop timeout (Gateway unreachable)";
        }, "Trace failed");
    }

    public String getGlobalPing(String host) {
        return executeCommand(List.of("ping", "-n", "3", host), (reader) -> {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Average")) {
                    return line.split("=")[1].trim();
                }
            }
            return "Request Timed Out";
        }, "Ping failed");
    }

    public String getActiveInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (ni.isUp() && !ni.isLoopback() && !ni.isVirtual()) {
                    return ni.getDisplayName();
                }
            }
        } catch (Exception e) {
            return "Unknown";
        }
        return "No active interface detected";
    }

    private String executeCommand(List<String> command, CommandParser parser, String errorMsg) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Command timed out";
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return parser.parse(reader);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Execution interrupted";
        } catch (Exception e) {
            return errorMsg;
        }
    }

    @FunctionalInterface
    interface CommandParser {
        String parse(BufferedReader reader) throws Exception;
    }
}
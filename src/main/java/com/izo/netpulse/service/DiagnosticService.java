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
import java.util.regex.Pattern;

/**
 * Service providing network diagnostic tools including ISP location lookups,
 * reachability tests, DNS performance metrics, and OS-level network commands.
 */
@Service
public class DiagnosticService {

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    /**
     * Regex to match standard IPv4 address formats.
     */
    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    /**
     * Retrieves geographic and ISP information based on the user's public IP address.
     * Uses the ip-api.com service.
     *
     * @return A formatted string containing City, Country, ISP, ASN, and Public IP.
     */
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

    /**
     * Checks if the application can establish a basic HTTP connection to the WAN.
     *
     * @return A status string indicating if Google is accessible via HTTP HEAD request.
     */
    public String checkWebReachability() {
        Request request = new Request.Builder().url("http://google.com").head().build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful() ? "Accessible (HTTP 200)" : "Limited Connectivity";
        } catch (Exception e) {
            return "No Internet Connection";
        }
    }

    /**
     * Parses Windows 'ipconfig' output to identify configured DNS server addresses.
     * Note: This method is platform-dependent.
     *
     * @return A string of space-separated DNS IP addresses.
     */
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

    /**
     * Measures the time taken to resolve a domain name to an IP address.
     *
     * @param domain The hostname to resolve (e.g., "google.com").
     * @return The resolution latency in milliseconds, or a failure message.
     */
    public String testDnsSpeed(String domain) {
        long start = System.currentTimeMillis();
        try {
            InetAddress.getByName(domain);
            return (System.currentTimeMillis() - start) + "ms";
        } catch (Exception e) {
            return "Resolution Failed (Offline)";
        }
    }

    /**
     * Executes a traceroute limited to the first hop to identify the local gateway.
     *
     * @return The IP or hostname of the first network hop.
     */
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

    /**
     * Performs a standard ICMP ping to a target host and parses the average latency.
     *
     * @param host The target IP or domain to ping.
     * @return The average round-trip time (RTT) as reported by the OS.
     */
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

    /**
     * Identifies the primary active non-virtual network interface (e.g., Wi-Fi, Ethernet).
     *
     * @return The display name of the active hardware interface.
     */
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

    /**
     * Helper method to execute system shell commands and process their output.
     *
     * @param command  The list of command arguments to execute.
     * @param parser   The logic used to extract information from the command output.
     * @param errorMsg The default message to return if execution fails.
     * @return The parsed result of the command execution.
     */
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

    /**
     * Functional interface for parsing BufferedReader output from system processes.
     */
    @FunctionalInterface
    interface CommandParser {
        String parse(BufferedReader reader) throws Exception;
    }
}
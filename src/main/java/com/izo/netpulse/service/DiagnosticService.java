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

@Service
public class DiagnosticService {

    private final OkHttpClient httpClient = new OkHttpClient();

    public String getServerLocation() {
        // Fetches ISP, City, Country, and ASN info
        Request request = new Request.Builder()
                .url("http://ip-api.com/line/?fields=status,message,country,city,isp,as,query")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "API Error: " + response.code();

            String[] lines = response.body().string().split("\n");
            if (lines[0].equalsIgnoreCase("success")) {
                // Format: City, Country (ISP) - ASN: ASXXXX - IP: 1.2.3.4
                return String.format("%s, %s (%s) \nASN: %s \nPublic IP: %s",
                        lines[2], lines[1], lines[3], lines[4], lines[5]);
            } else {
                return "Location Error: " + lines[1];
            }
        } catch (Exception e) {
            return "Could not fetch location: " + e.getMessage();
        }
    }

    public String checkWebReachability() {
        Request request = new Request.Builder().url("http://google.com").head().build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful() ? "Accessible (HTTP 200)" : "Blocked/Limited";
        } catch (Exception e) {
            return "Unreachable";
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
            return "Resolution Failed";
        }
    }

    public String getFirstHop() {
        try {
            Process process = Runtime.getRuntime().exec("tracert -d -h 1 8.8.8.8");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("1 ")) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            return "Could not detect first hop";
        }
        return "First hop timeout";
    }

    public String getGlobalPing(String host) {
        try {
            Process process = Runtime.getRuntime().exec("ping -n 3 " + host);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Average")) {
                    return line.split("=")[1].trim();
                }
            }
        } catch (Exception e) {
            return "Error";
        }
        return "Timed out";
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
        return "No active interface";
    }
}
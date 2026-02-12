# NetPulse

NetPulse is a high-performance network utility designed to bridge the gap between simple browser-based tests and complex system diagnostics. Built on a Spring Boot backbone with a reactive JavaFX frontend, it provides pinpoint accuracy for speed testing and deep-tissue network analysis.

<p align="center">
  <a href="https://github.com/IZO-Ong/netpulse/releases"><b>Download Latest Release</b></a>
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-orange?style=flat-square" />
  <img alt="Spring Boot 3.4.0" src="https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen?style=flat-square" />
  <img alt="JavaFX 21" src="https://img.shields.io/badge/JavaFX-21-blue?style=flat-square" />
</p>

---

## Features

* **Real-Time Throughput** -- Live data streaming via OkHttp for authentic download and upload benchmarking.
* **Deep Diagnostics** -- Multi-layer scans including DNS latency, ISP mapping and ASN data.
* **Performance Archiving** -- Integrated H2/JPA database to track connection health over weeks or months.
* **Hybrid Analysis** -- Combines application-layer HTTP requests with OS-level ICMP tools (Ping/Traceroute).

---

## Architecture

NetPulse follows a decoupled architecture ensuring the UI remains responsive even during heavy network saturation.

```
JavaFX UI (NetPulseController)
             │          │
             ▼          ▼
    SpeedTestService  DiagnosticService ──► System OS (Ping/Tracert)
             │          │
             ▼          ▼
    SpeedRepository ◄── HTTP Client (OkHttp) ──► External APIs
             │
             ▼
      H2 Local Database
```


## User Guide

### 1. Speed Testing
Navigate to the Dashboard to initiate a test. NetPulse uses a moving average algorithm to bypass initial TCP "cold start" spikes, providing a stabilized Mbps reading.

* **Latency (Ping):** Measured via high-frequency HEAD requests to global edge nodes.
* **Visual Gauges:** Adaptive UI that scales based on your current bandwidth.

<p align="center">
  <img src="./assets/screenshot-speedtest.png" width="300" alt="Speed Test UI">
</p>

### 2. Diagnostic Suite
The Diagnostics tab identifies bottlenecks beyond just raw speed:

* **Network Layer:** Checks Hop 1 (Gateway) response times to see if connectivity issues stem from your router or your ISP.
* **Identity Mapping:** Automatically resolves your Public IP, ASN, and ISP metadata.

<p align="center">
  <img src="./assets/screenshot-diagnostics.png" width="300" alt="Diagnostics UI">
</p>

### 3. Historical Tracking
All tests are automatically timestamped and stored. Use the History Tab to view performance trends on a dynamic line chart, helping you identify ISP throttling during peak hours.

<p align="center">
  <img src="./assets/screenshot-history.png" width="300" alt="History UI">
</p>

### 4. Settings and Customization
* **Automated Health Checks:** Set NetPulse to run in the background and alert you if connectivity drops.
* **Theming:** Switch between Onyx Dark and Clean Light modes via the Settings panel.

<p align="center">
  <img src="./assets/screenshot-light-mode.png" width="300" alt="Light Mode UI">
</p>

---


## Getting Started

### Prerequisites
- **Java 21** must be installed on your system.
- For Windows users, ensure you have permissions to run `ping` and `tracert` in your terminal.

### 1. Download and Run (Recommended)
1. Go to the [Releases](https://github.com/IZO-Ong/netpulse/releases) page.
2. Download the `netpulse-0.0.1-SNAPSHOT.jar`.
3. Launch the application:
```bash
java -jar netpulse-0.0.1-SNAPSHOT.jar
```

### 2. Build from Source
```bash
git clone [https://github.com/IZO-Ong/netpulse.git](https://github.com/IZO-Ong/netpulse.git)
cd netpulse
mvn clean package
java -jar target/netpulse-0.0.1-SNAPSHOT.jar
```

---
## Tech Stack
- Frontend: JavaFX, FXML, CSS3
- Backend: Spring Boot 3.4.0, Spring Data JPA
- Database: H2 (Embedded)
- Networking: OkHttp 4.x
- Build Tool: Maven
---

## About
- Created by Isaac Ong

---

## License
See [LICENSE](LICENSE).
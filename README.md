# 🛡️ Sentria Agent

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mohamedikbel_sentria-agent&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=mohamedikbel_sentria-agent)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Sentria** is a lightweight, self-hosted hardware monitoring agent for your PC. It continuously tracks system health, detects anomalies, sends mobile notifications via [ntfy](https://ntfy.sh), and exposes a local [MCP server](https://modelcontextprotocol.io) so your AI assistant (Claude, Cursor, Continue…) can query your machine's state in real time.

---

## ✨ Features

| Category | What Sentria does |
|---|---|
| **Hardware monitoring** | CPU usage & temperature, RAM usage, battery level/charging/time remaining, storage used/free, network download/upload |
| **SSD health** | SMART data via `smartctl` (full), OSHI fallback — reports health %, total bytes written |
| **Behavior detection** | Detects gaming sessions (Steam, etc.), video editing (Premiere, etc.), heavy disk write activity, idle time |
| **Findings & alerts** | Creates findings when battery is fully charged, SSD wear is accelerating; deduplicates alerts |
| **AI-formatted notifications** | Sends concise, human-readable summaries to your phone via ntfy — optionally formatted by GPT-4o-mini, Claude, Llama or any OpenAI-compatible model |
| **Periodic trend reports** | Long-period analysis (1 / 3 / 7 / 14 / 21 / 30 days) with behavior insights and process breakdown |
| **MCP server** | 5 built-in tools queryable by any MCP-compatible AI client: system status, metric trends, top processes, recent findings, SSD/storage health |
| **Automatic data retention** | Configurable rolling retention window (default 30 days); automatic cleanup every 24 h |
| **Guided interactive setup** | `--setup` wizard that walks you through every option and writes a config file |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Sentria Agent                            │
│                                                                 │
│  Collectors (plug-in)         Detection Services               │
│  ├─ HardwareCollectorService  ├─ BatteryFindingService         │
│  └─ SsdCollectorService       ├─ SsdFindingService             │
│          │                    └─ BehaviorDetectionService       │
│          ▼                             │                        │
│  MetricCollectionOrchestrator          │                        │
│          │                            │                        │
│          ▼                            ▼                        │
│       SQLite DB ◄──────────── FindingStore / BehaviorStore     │
│          │                                                      │
│          ├─► GlobalMetricsSummaryService ──► ntfy notification  │
│          ├─► PeriodicMetricVerdictService ──► ntfy notification  │
│          └─► MCP Server (SSE) ◄── AI client (Claude / Cursor…)  │
└─────────────────────────────────────────────────────────────────┘
```

**Key contracts:**
- `MetricCollectorPlugin` — implement this interface to add any new metric source in one class
- `MetricSnapshotStore` / `FindingStore` / `BehaviorSessionStore` — persistence ports
- `NotificationSender` — notification transport abstraction

---

## 📋 Prerequisites

| Requirement | Version |
|---|---|
| Java (JDK) | 21 or newer |
| Maven (or use the wrapper `mvnw`) | 3.9+ |
| OS | Windows (primary), macOS, Linux supported |
| ntfy app | [Android](https://play.google.com/store/apps/details?id=io.heckel.ntfy) / [iOS](https://apps.apple.com/app/ntfy/id1625396347) (optional but recommended) |
| smartmontools | Optional — needed for full SMART SSD data |

---

## 🚀 Quick Start

### 1 — Build

```powershell
./mvnw.cmd clean package -DskipTests
```

The JAR is output to `target/sentria-agent-1.0.0.jar`.

### 2 — Guided setup (recommended for first run)

```powershell
java -jar target/sentria-agent-1.0.0.jar --setup
```

The wizard will ask you for:
1. **Device name** — auto-detected from hostname
2. **ntfy topic** — the topic you subscribe to in the ntfy app
3. **Monitoring scope** — all collectors, or pick hardware / SSD individually
4. **AI mode** — enable/disable AI formatting; choose provider and model
5. **Report periods** — global state interval (minutes) and long-period cycle (days)
6. **Data retention** — how many days to keep metrics in the local database
7. **Windows startup** — optionally create a startup shortcut

Config is saved to **`~/.sentria/sentria-user.properties`** (stable across restarts) and `./config/sentria-user.properties` (local override).

### 3 — Run

```powershell
java -jar target/sentria-agent-1.0.0.jar
```

---

## ⚙️ Configuration Reference

All properties can be set in `~/.sentria/sentria-user.properties` (or `./config/sentria-user.properties`). Values below are the defaults from `application.properties`.

### Application

| Property | Default | Description |
|---|---|---|
| `app.device-name` | `%COMPUTERNAME%` | Device identifier shown in logs and notifications |

### Monitoring

| Property | Default | Description |
|---|---|---|
| `monitoring.enabled` | `true` | Master switch for all collection |
| `monitoring.interval-seconds` | `10` | How often metrics are collected (seconds) |
| `monitoring.collectors` | `hardware,ssd` | Comma-separated list of active collectors (`hardware`, `ssd`) |
| `monitoring.summary-interval-seconds` | `180` | How often the global state snapshot is sent (seconds) |
| `monitoring.process-interval-seconds` | `60` | How often top-processes are captured |
| `monitoring.verdict-interval-seconds` | `86400` | How often the long-period report is sent (seconds) |
| `monitoring.verdict-report-days` | `3` | Analysis window for the periodic report (days) |
| `monitoring.verdict-top-processes` | `5` | Max processes shown in the periodic report |
| `monitoring.retention-days` | `30` | Rolling data retention window (days) |
| `monitoring.battery-full-percent-threshold` | `99.5` | Battery % above which "fully charged" alert fires |
| `monitoring.ssd-low-health-percent-threshold` | `20` | SSD health % below which alert fires |
| `monitoring.ssd-high-write-delta-gb-threshold` | `5` | GB written per interval that triggers "high write" signal |
| `monitoring.storage-low-free-gb-threshold` | `15` | GB free below which storage alert fires |

### Notifications (ntfy)

| Property | Default | Description |
|---|---|---|
| `notifications.provider` | `ntfy` | Notification provider (only `ntfy` supported) |
| `notifications.ntfy.enabled` | `true` | Enable/disable push notifications |
| `notifications.ntfy.server-url` | `https://ntfy.sh` | ntfy server (use your self-hosted server URL if needed) |
| `notifications.ntfy.topic` | `sentria-demo-topic` | ntfy topic — **change this** and subscribe to it in the app |

### AI Formatting

| Property | Default | Description |
|---|---|---|
| `ai.enabled` | `true` | Enable AI-formatted notifications |
| `ai.provider` | `openai` | Provider: `openai` \| `openrouter` \| `groq` \| `custom` |
| `ai.model` | `gpt-4o-mini` | Model name (provider-specific) |
| `ai.timeout-seconds` | `30` | HTTP timeout for AI API calls |
| `ai.openai-api-key` | _(empty)_ | OpenAI API key (or set `OPENAI_API_KEY` env var) |
| `ai.openrouter-api-key` | _(empty)_ | OpenRouter API key |
| `ai.groq-api-key` | _(empty)_ | Groq API key |
| `ai.custom-api-key` | _(empty)_ | Custom OpenAI-compatible API key |
| `ai.custom-base-url` | _(empty)_ | Custom base URL (e.g. `http://localhost:11434/v1` for Ollama) |
| `ai.formatting.max-consecutive-auth-failures` | `2` | After this many 401 errors, AI formatting is suspended |
| `ai.formatting.cooldown-seconds` | `1800` | Cooldown duration after repeated auth failures (seconds) |

### SSD / SMART

| Property | Default | Description |
|---|---|---|
| `storage.smart.enabled` | `true` | Allow smartctl detection |
| `storage.smart.prefer-smartctl` | `true` | Prefer SMART data over OSHI when available |
| `storage.smart.binary-path` | _(empty)_ | Explicit path to `smartctl` binary (auto-detected if blank) |
| `storage.smart.discovery-enabled` | `true` | Auto-discover all connected drives |

### Database

| Property | Default | Description |
|---|---|---|
| `spring.datasource.url` | `jdbc:sqlite:~/sentria.db` | SQLite database path (override with `SENTRIA_DB_PATH` env var) |

---

## 📱 ntfy Notifications Setup

1. Install the **ntfy** app: [Android](https://play.google.com/store/apps/details?id=io.heckel.ntfy) · [iOS](https://apps.apple.com/app/ntfy/id1625396347) · [Web](https://ntfy.sh/app)
2. Subscribe to your chosen topic (e.g. `my-sentria-pc`)
3. Set in your config:
   ```properties
   notifications.ntfy.topic=my-sentria-pc
   notifications.ntfy.enabled=true
   ```
4. Sentria sends two types of push notifications:
   - **Global State** — short health snapshot every N minutes (configurable)
   - **Long-Period Report** — trend analysis every N days (configurable)
   - **Findings** — instant alerts for battery full, SSD wear acceleration

> **Self-hosted ntfy:** set `notifications.ntfy.server-url=https://your-server.com` to use your own ntfy instance.

---

## 🤖 AI Integration

Sentria uses AI to format notifications from a structured technical summary into a clean, human-friendly message. AI formatting is **gracefully degraded** — if the API key is invalid or the request fails, Sentria falls back to its built-in template automatically.

### Supported providers

| Provider | `ai.provider` value | Recommended model |
|---|---|---|
| OpenAI | `openai` | `gpt-4o-mini`, `gpt-4.1-mini` |
| OpenRouter | `openrouter` | `openai/gpt-4o-mini`, `anthropic/claude-3.5-sonnet` |
| Groq | `groq` | `llama-3.1-8b-instant`, `llama-3.3-70b-versatile` |
| Any OpenAI-compatible | `custom` | Your model name |

### Local model (Ollama example)

```properties
ai.provider=custom
ai.custom-base-url=http://localhost:11434/v1
ai.custom-api-key=ollama
ai.model=llama3.2
```

### Disable AI (static templates only)

```properties
ai.enabled=false
```

---

## 🔌 MCP Server

Sentria embeds a **Model Context Protocol (MCP) server** that lets any compatible AI assistant (Claude Desktop, Cursor, Continue, etc.) query your machine's live data directly.

### Connection

| Setting | Value |
|---|---|
| **SSE endpoint** | `http://localhost:8080/mcp/sse` |
| **Message endpoint** | `http://localhost:8080/mcp/messages` |
| **Protocol** | MCP over SSE (HTTP) |

### Available tools

| Tool | Description |
|---|---|
| `sentriaGetSystemStatus` | Current snapshot of all metrics (CPU, RAM, battery, SSD, storage, network) |
| `sentriaGetMetricsWindow` | Trends for any metric over the last N minutes (avg, min, max, delta) |
| `sentriaGetTopProcesses` | Most CPU/RAM-intensive processes over the last N minutes |
| `sentriaGetRecentFindings` | Recent findings (battery full, SSD wear) over the last N hours |
| `sentriaGetStorageSsdHealth` | Focused SSD + storage health view |

### Claude Desktop configuration example

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "sentria": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://localhost:8080/mcp/sse"
      ]
    }
  }
}
```

---

## 📊 Monitored Metrics

| Metric | Unit | Collector |
|---|---|---|
| CPU Usage | % | hardware |
| CPU Temperature | °C | hardware |
| RAM Usage | % | hardware |
| Battery Level | % | hardware |
| Battery Charging State | on/off | hardware |
| Battery Time Remaining | min | hardware |
| Storage Usage | % | hardware |
| Storage Free Space | GB | hardware |
| Network Download | MB/s | hardware |
| Network Upload | MB/s | hardware |
| SSD Health | % | ssd |
| SSD Total Written | GB | ssd |

---

## 🧠 Behavior Detection

Sentria automatically detects usage patterns by watching running processes and disk writes:

| Behavior | Trigger |
|---|---|
| **Gaming** | Steam, game executables detected |
| **Video Editing** | Adobe Premiere, DaVinci Resolve, etc. detected |
| **Heavy Disk Write** | SSD write delta ≥ 5 GB between two consecutive samples |
| **Idle** | No gaming or video editing session active |

Detected sessions are stored in the database and correlated with SSD wear findings to identify likely contributors.

---

## 🔍 Findings & Alerts

Sentria creates and deduplicates findings for the following conditions:

| Finding | Trigger | Severity |
|---|---|---|
| **Battery Fully Charged** | Battery ≥ 99.5% and charging | Low |
| **SSD Wear Accelerating** | SSD health dropped ≥ 3% over the configured window | High |

Each finding is sent as a push notification exactly once per configurable deduplication window, then suppressed until conditions change.

---

## 🗄️ Data Storage — SQLite, Zero Config Required

Sentria uses **SQLite** as its embedded database engine. No server to install, no credentials to manage — the database is a single file on your machine.

### Why SQLite?

| Advantage | Details |
|---|---|
| 🔧 **Zero setup** | No database server, no service to start — just a `.db` file |
| 📦 **Fully embedded** | SQLite ships inside the JAR via `sqlite-jdbc`, nothing extra to install |
| 💾 **Tiny footprint** | The database file stays small (typically a few MB for 30 days of metrics) |
| 🔒 **Local & private** | All your hardware data stays on your machine, never sent anywhere |
| 🚀 **Fast for this workload** | Time-series inserts + small aggregation queries — SQLite handles this perfectly |
| 🔁 **Portable** | Copy/move `sentria.db` to another machine and your full history comes with it |
| 🛠️ **Inspectable** | Open the file with any SQLite browser (e.g. [DB Browser for SQLite](https://sqlitebrowser.org/)) to query your metrics manually |

### Storage Details

- **Location:** `~/sentria.db` by default — override with the `SENTRIA_DB_PATH` environment variable
- **Schema migrations:** Managed automatically by **Flyway** on every startup — upgrading Sentria never requires manual SQL
- **Automatic retention:** Metrics older than `monitoring.retention-days` (default: **30 days**) are purged every 24 hours, keeping the file compact
- **Test isolation:** Integration tests use a separate `target/sentria-test.db` that is never mixed with your real data

---

## 🧪 Running Tests

```powershell
# Unit tests only (fast, no Spring context)
./mvnw.cmd test

# Full suite including integration tests
./mvnw.cmd verify
```

**Test coverage:** 85 tests across 13 test classes — unit tests use Mockito, integration tests use a dedicated SQLite test database at `./target/sentria-test.db`.

---

## 🛠️ Adding a New Collector

Implement `MetricCollectorPlugin` and annotate with `@Service`:

```java
@Service
@DependsOn("flyway")
public class NetworkCollectorService implements MetricCollectorPlugin {

    @Override
    public String collectorName() { return "network"; }

    @Override
    public List<MetricSnapshot> collect(Instant collectedAt) {
        // build and return MetricSnapshot list
    }
}
```

That's it — the `MetricCollectionOrchestrator` picks it up automatically.

---

## 🔐 Environment Variables

| Variable | Used for |
|---|---|
| `OPENAI_API_KEY` | OpenAI API key |
| `OPENROUTER_API_KEY` | OpenRouter API key |
| `CUSTOM_AI_API_KEY` | Custom AI provider key |
| `CUSTOM_AI_BASE_URL` | Custom AI base URL |
| `SENTRIA_DB_PATH` | Custom SQLite database path |
| `COMPUTERNAME` / `HOSTNAME` | Auto device name detection |

---

## 📁 Project Structure

```
sentria-agent/
├── src/main/java/com/sentria/
│   ├── application/
│   │   ├── monitoring/      # Orchestrator, collectors, retention, summaries
│   │   ├── behavior/        # Process capture, behavior port
│   │   └── port/            # Output ports (store interfaces)
│   ├── behavior/            # Behavior detection services
│   ├── bootstrap/           # Startup, guided setup CLI
│   ├── config/              # @ConfigurationProperties records
│   ├── context/             # Behavior correlation
│   ├── domain/              # Pure domain objects (MetricSnapshot, Finding, …)
│   ├── finding/             # Battery and SSD finding services
│   ├── infrastructure/
│   │   ├── monitoring/      # OSHI + smartctl providers
│   │   └── persistence/     # JDBC repositories
│   ├── mcp/                 # MCP tools configuration
│   ├── notification/        # ntfy sender, formatters
│   └── ai/                  # OpenAI-compatible client, formatters
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/        # Flyway SQL migrations
└── src/test/                # 85 unit + integration tests
```

---

## 📄 License

MIT — see [LICENSE](LICENSE).

---

> **Sentria** — _Know your machine before it fails._







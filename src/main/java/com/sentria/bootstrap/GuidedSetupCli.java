package com.sentria.bootstrap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public final class GuidedSetupCli {

    private static final String PROVIDER_OPENAI     = "openai";
    private static final String PROVIDER_OPENROUTER = "openrouter";
    private static final String PROVIDER_GROQ       = "groq";
    private static final String PROVIDER_CUSTOM     = "custom";
    private static final String DEFAULT_MODEL       = "gpt-4o-mini";
    private static final String BASE_URL_OPENAI     = "https://api.openai.com/v1";

    private GuidedSetupCli() {
    }

    public static void run(String[] args) {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.println("Sentria guided setup");
        System.out.println("This wizard prepares a production-ready config in ./config/sentria-user.properties");
        System.out.println("MCP server stays enabled by default for AI workflow integration.");
        System.out.println("You can press ENTER to keep defaults at each step.");
        System.out.println("Important: install the ntfy mobile app first (Android/iOS), then subscribe to your topic.");
        System.out.println("Global state report = short health snapshot every X minutes.");
        System.out.println("Periodic report = long-term trend analysis every X days (1/3/7/14/21/30).\n");
        System.out.println("MCP note: server is enabled by default.");
        System.out.println("Suggested MCP SSE URL in clients: http://localhost:8080/sse");
        System.out.println("Server sends MCP message endpoint automatically (example: /mcp/message).");
        System.out.println("MCP docs: https://modelcontextprotocol.io\n");
        System.out.println();

        printSection("1/7 Device");

        String deviceName = ask(scanner,
                "Device name",
                detectDeviceName());

        String notifyTopic = ask(scanner,
                "Notification topic (ntfy)",
                "sentria-demo-topic");

        String notifyServer = ask(scanner,
                "Notification server URL",
                "https://ntfy.sh");

        printSection("2/7 Monitoring scope");
        Map<String, String> availableCollectors = new LinkedHashMap<>();
        availableCollectors.put("hardware", "CPU, RAM, battery, network, storage, temperature");
        availableCollectors.put("ssd", "SSD health and SSD written data (SMART/OSHI hybrid)");
        List<String> selectedCollectors = askCollectors(scanner, availableCollectors);

        printSection("3/7 AI mode");
        boolean aiEnabled = askYesNo(scanner,
                "Enable AI formatting (if no = static templates only)? (y/n)",
                true);

        String aiProvider = PROVIDER_OPENAI;
        String aiModel = DEFAULT_MODEL;
        String openaiKey = "";
        String openrouterKey = "";
        String groqKey = "";
        String customKey = "";
        String customBaseUrl = "";

        if (aiEnabled) {
            aiProvider = askChoice(scanner,
                    "AI provider",
                    List.of(PROVIDER_OPENAI, PROVIDER_OPENROUTER, PROVIDER_GROQ, PROVIDER_CUSTOM),
                    PROVIDER_OPENAI);

            aiModel = askAiModelByNumber(scanner, aiProvider);

            switch (aiProvider) {
                case PROVIDER_OPENROUTER -> openrouterKey = ask(scanner, "OpenRouter API key", "");
                case PROVIDER_GROQ -> groqKey = ask(scanner, "Groq API key", "");
                case PROVIDER_CUSTOM -> {
                    customBaseUrl = ask(scanner, "Custom OpenAI-compatible base URL", BASE_URL_OPENAI);
                    customKey = ask(scanner, "Custom API key", "");
                }
                default -> openaiKey = ask(scanner, "OpenAI API key", "");
            }

            boolean testNow = askYesNo(scanner,
                    "Test API key now (live request)? (y/n)",
                    true);
            if (testNow) {
                String effectiveKey = switch (aiProvider) {
                    case PROVIDER_OPENROUTER -> openrouterKey;
                    case PROVIDER_GROQ -> groqKey;
                    case PROVIDER_CUSTOM -> customKey;
                    default -> openaiKey;
                };
                String effectiveBaseUrl = providerBaseUrl(aiProvider, customBaseUrl);

                ValidationResult result = validateAiKeyLive(effectiveBaseUrl, effectiveKey);
                if (result.ok()) {
                    System.out.println("AI key check: OK (" + result.message() + ")");
                } else {
                    System.out.println("AI key check: FAILED (" + result.message() + ")");
                    System.out.println("You can continue setup, or rerun --setup later to update key/provider.");
                }
            }
        }

        printSection("4/7 Report periods");

        int summaryMinutes = askInt(scanner,
                "Global state period in minutes (example: 2 for every 2 minutes)",
                3,
                1,
                1440);

        int cycleDays = askChoiceInt(scanner,
                "Long-period report cycle days",
                List.of(1, 3, 7, 14, 21, 30),
                3);

        printSection("5/7 Retention");
        int retentionDays = askInt(scanner,
                "Data retention in days",
                30,
                1,
                3650);

        printSection("6/7 Startup");
        boolean launchAtStartup = askYesNo(scanner,
                "Launch app at Windows startup? (y/n)",
                false);

        printSection("7/7 Save");

        List<String> lines = new ArrayList<>();
        lines.add("app.device-name=" + deviceName);
        lines.add("notifications.provider=ntfy");
        lines.add("notifications.ntfy.enabled=true");
        lines.add("notifications.ntfy.server-url=" + notifyServer);
        lines.add("notifications.ntfy.topic=" + notifyTopic);
        lines.add("monitoring.collectors=" + String.join(",", selectedCollectors));
        lines.add("monitoring.summary-interval-seconds=" + (summaryMinutes * 60));
        lines.add("monitoring.verdict-report-days=" + cycleDays);
        lines.add("monitoring.verdict-interval-seconds=" + (cycleDays * 86400));
        lines.add("monitoring.retention-days=" + retentionDays);
        lines.add("ai.enabled=" + aiEnabled);
        lines.add("ai.provider=" + aiProvider);
        lines.add("ai.model=" + aiModel);
        lines.add("ai.openai-api-key=" + openaiKey);
        lines.add("ai.openrouter-api-key=" + openrouterKey);
        lines.add("ai.groq-api-key=" + groqKey);
        lines.add("ai.custom-api-key=" + customKey);
        lines.add("ai.custom-base-url=" + customBaseUrl);
        lines.add("spring.ai.mcp.server.enabled=true");

        List<Path> writtenFiles = writeUserConfig(lines);
        if (launchAtStartup) {
            createWindowsStartupScript();
        }

        System.out.println();
        System.out.println("Setup complete.");
        for (Path path : writtenFiles) {
            System.out.println("Config saved: " + path.toAbsolutePath());
        }
        System.out.println("Enabled monitoring collectors: " + String.join(", ", selectedCollectors));
        System.out.println("AI mode: " + (aiEnabled ? ("enabled (" + aiProvider + ", model=" + aiModel + ")") : "disabled (static mode)"));
        System.out.println("MCP server: enabled by default. Use SSE URL: http://localhost:8080/sse");
        System.out.println("MCP message endpoint is announced by the SSE stream (example: /mcp/message).");
        System.out.println("Notification reminder: install ntfy mobile app then subscribe to topic '" + notifyTopic + "'.");
        System.out.println("If you set 2 minutes above, startup log should show: Global summary interval: 120 seconds.");
        if (launchAtStartup) {
            System.out.println("Windows startup shortcut script generated.");
        }
        System.out.println("Now run the app normally without --setup.");

        printReadyChecklist(
                notifyServer,
                notifyTopic,
                summaryMinutes,
                cycleDays,
                selectedCollectors,
                aiEnabled,
                aiProvider,
                aiModel
        );
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    private static String detectDeviceName() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return host.trim();
            }
        } catch (Exception ignored) {
        }

        String env = System.getenv("COMPUTERNAME");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        return "My PC";
    }

    private static List<Path> writeUserConfig(List<String> lines) {
        List<Path> written = new ArrayList<>();
        try {
            Path configDir = Path.of("config");
            Files.createDirectories(configDir);
            Path file = configDir.resolve("sentria-user.properties");
            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            written.add(file);

            Path homeFile = Path.of(System.getProperty("user.home"), ".sentria", "sentria-user.properties");
            Files.createDirectories(homeFile.getParent());
            Files.write(homeFile, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            written.add(homeFile);

            return written;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write setup configuration", e);
        }
    }

    private static void createWindowsStartupScript() {
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) {
                return;
            }

            Path startupDir = Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
            Files.createDirectories(startupDir);
            Path script = startupDir.resolve("SentriaAgentStart.bat");

            String currentDir = Path.of(".").toAbsolutePath().normalize().toString();
            String content = "@echo off\r\n"
                    + "cd /d \"" + currentDir + "\"\r\n"
                    + "start \"Sentria Agent\" java -jar sentria-agent-0.0.1-SNAPSHOT.jar\r\n";

            Files.writeString(script, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create startup script", e);
        }
    }

    private static String ask(Scanner scanner, String label, String defaultValue) {
        System.out.print(label + " [" + defaultValue + "]: ");
        String input = scanner.nextLine();
        if (input == null || input.isBlank()) {
            return defaultValue;
        }
        return input.trim();
    }

    private static boolean askYesNo(Scanner scanner, String label, boolean defaultValue) {
        String defaultText = defaultValue ? "y" : "n";
        while (true) {
            String value = ask(scanner, label, defaultText).toLowerCase(Locale.ROOT);
            if (value.equals("y") || value.equals("yes")) {
                return true;
            }
            if (value.equals("n") || value.equals("no")) {
                return false;
            }
            System.out.println("Please answer y or n.");
        }
    }

    private static int askInt(Scanner scanner, String label, int defaultValue, int min, int max) {
        while (true) {
            String raw = ask(scanner, label, Integer.toString(defaultValue));
            try {
                int value = Integer.parseInt(raw);
                if (value < min || value > max) {
                    System.out.println("Value must be between " + min + " and " + max + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
            }
        }
    }

    private static String askChoice(Scanner scanner, String label, List<String> choices, String defaultChoice) {
        System.out.println(label + " options: " + String.join(", ", choices));
        while (true) {
            String value = ask(scanner, label, defaultChoice).toLowerCase(Locale.ROOT);
            if (choices.contains(value)) {
                return value;
            }
            System.out.println("Invalid choice.");
        }
    }

    private static List<String> askCollectors(Scanner scanner, Map<String, String> availableCollectors) {
        List<String> keys = new ArrayList<>(availableCollectors.keySet());

        System.out.println("Available monitoring collectors:");
        for (int i = 0; i < keys.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + keys.get(i) + " - " + availableCollectors.get(keys.get(i)));
        }
        System.out.println("Type 'all' for all collectors, or indexes like: 1,2");
        System.out.println("Example: choose only SSD with '2', or only hardware with '1'.");

        while (true) {
            String value = ask(scanner, "Collectors to enable", "all").toLowerCase(Locale.ROOT).trim();
            if (value.equals("all")) {
                return keys;
            }
            List<String> result = parseCollectorTokens(value, keys);
            if (result != null) {
                return result;
            }
            System.out.println("Invalid collector selection. Example: all OR 1 OR 1,2");
        }
    }

    /**
     * Parses a comma-separated list of 1-based collector indexes (e.g. "1,2") into
     * the corresponding collector names. Returns {@code null} if the input is invalid.
     */
    private static List<String> parseCollectorTokens(String value, List<String> keys) {
        String[] tokens = value.split(",");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            try {
                int index = Integer.parseInt(trimmed);
                if (index < 1 || index > keys.size()) {
                    return null;
                }
                String collector = keys.get(index - 1);
                if (!result.contains(collector)) {
                    result.add(collector);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static int askChoiceInt(Scanner scanner, String label, List<Integer> choices, int defaultValue) {
        System.out.println(label + " options: " + choices);
        while (true) {
            int value = askInt(scanner, label, defaultValue, 1, 3650);
            if (choices.contains(value)) {
                return value;
            }
            System.out.println("Choose one of: " + choices);
        }
    }

    private static String defaultModel(String provider) {
        return switch (provider) {
            case PROVIDER_OPENROUTER -> "openai/gpt-4o-mini";
            case PROVIDER_GROQ -> "llama-3.1-8b-instant";
            case PROVIDER_CUSTOM -> DEFAULT_MODEL;
            default -> DEFAULT_MODEL;
        };
    }

    private static void printReadyChecklist(String notifyServer,
                                            String notifyTopic,
                                            int summaryMinutes,
                                            int cycleDays,
                                            List<String> selectedCollectors,
                                            boolean aiEnabled,
                                            String aiProvider,
                                            String aiModel) {
        System.out.println();
        System.out.println("================ READY-TO-RUN CHECKLIST ================");
        System.out.println("1) Mobile notifications");
        System.out.println("   - Install ntfy mobile app (Android/iOS): https://ntfy.sh/app");
        System.out.println("   - Subscribe to topic: " + notifyTopic);
        System.out.println("   - Server: " + notifyServer);
        System.out.println();
        System.out.println("2) Monitoring profile");
        System.out.println("   - Collectors: " + String.join(", ", selectedCollectors));
        System.out.println("   - Global state interval: " + summaryMinutes + " minute(s) (" + (summaryMinutes * 60) + " seconds)");
        System.out.println("   - Periodic report interval: every " + cycleDays + " day(s)");
        System.out.println("   - Global state = short health snapshot");
        System.out.println("   - Periodic report = long-term trends + behavior analysis");
        System.out.println();
        System.out.println("3) AI mode");
        if (aiEnabled) {
            System.out.println("   - Enabled: yes");
            System.out.println("   - Provider: " + aiProvider);
            System.out.println("   - Model: " + aiModel);
        } else {
            System.out.println("   - Enabled: no (static templates mode)");
        }
        System.out.println();
        System.out.println("4) MCP integration");
        System.out.println("   - MCP server: enabled by default");
        System.out.println("   - Suggested MCP SSE URL: http://localhost:8080/sse");
        System.out.println("   - MCP message endpoint: announced by SSE (example: /mcp/message)");
        System.out.println("   - MCP docs: https://modelcontextprotocol.io");
        System.out.println();
        System.out.println("5) Start command");
        System.out.println("   - java -jar target/sentria-agent-0.0.1-SNAPSHOT.jar");
        System.out.println("========================================================");
    }

    private static String providerBaseUrl(String provider, String customBaseUrl) {
        return switch (provider) {
            case PROVIDER_OPENROUTER -> "https://openrouter.ai/api/v1";
            case PROVIDER_GROQ -> "https://api.groq.com/openai/v1";
            case PROVIDER_CUSTOM -> (customBaseUrl == null || customBaseUrl.isBlank()) ? BASE_URL_OPENAI : customBaseUrl.trim();
            default -> BASE_URL_OPENAI;
        };
    }

    private static ValidationResult validateAiKeyLive(String baseUrl, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return new ValidationResult(false, "empty API key");
        }

        try {
            String url = normalizeBaseUrl(baseUrl) + "/models";
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                return new ValidationResult(true, "HTTP " + code);
            }

            String body = response.body() == null ? "" : response.body();
            if (body.length() > 180) {
                body = body.substring(0, 180) + "...";
            }
            return new ValidationResult(false, "HTTP " + code + " - " + body.replace("\n", " "));
        } catch (Exception e) {
            return new ValidationResult(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return BASE_URL_OPENAI;
        }

        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private record ValidationResult(boolean ok, String message) {
    }

    private static String askAiModelByNumber(Scanner scanner, String provider) {
        List<String> models = switch (provider) {
            case PROVIDER_OPENROUTER -> List.of(
                    "openai/gpt-4o-mini",
                    "openai/gpt-4.1-mini",
                    "anthropic/claude-3.5-sonnet"
            );
            case PROVIDER_GROQ -> List.of(
                    "llama-3.1-8b-instant",
                    "llama-3.3-70b-versatile",
                    "mixtral-8x7b-32768"
            );
            case PROVIDER_CUSTOM -> List.of(defaultModel(provider));
            default -> List.of(
                    DEFAULT_MODEL,
                    "gpt-4.1-mini",
                    "gpt-4.1"
            );
        };

        System.out.println("Choose AI model by number:");
        for (int i = 0; i < models.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + models.get(i));
        }
        System.out.println("  " + (models.size() + 1) + ") custom model name");

        int choice = askInt(scanner,
                "Model number",
                1,
                1,
                models.size() + 1);

        if (choice == models.size() + 1) {
            return ask(scanner, "Enter custom model", defaultModel(provider));
        }

        return models.get(choice - 1);
    }
}

















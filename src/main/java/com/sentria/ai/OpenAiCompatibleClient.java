package com.sentria.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentria.config.AiProviderProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient {

    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper;

    public Optional<String> complete(String prompt) {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        ProviderConfig provider = resolveProvider();
        if (provider.apiKey().isBlank() || provider.baseUrl().isBlank()) {
            return Optional.empty();
        }

        try {
            ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("model", provider.model());
            payloadNode.set("messages", objectMapper.createArrayNode().add(
                            objectMapper.createObjectNode()
                                    .put("role", "user")
                                    .put("content", prompt)
                    ));
            payloadNode.put("temperature", 0.2);
            String payload = payloadNode.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(provider.baseUrl()) + "/chat/completions"))
                    .header("Authorization", "Bearer " + provider.apiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(Math.max(5, properties.timeoutSeconds())))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(5, properties.timeoutSeconds())))
                    .build()) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("AI HTTP " + response.statusCode() + " - " + trim(response.body()));
                }

                JsonNode root = objectMapper.readTree(response.body());
                String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
                if (content.isBlank()) {
                    return Optional.empty();
                }

                return Optional.of(content);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI request failed", e);
        } catch (IOException e) {
            throw new IllegalStateException("AI request failed", e);
        }
    }

    private ProviderConfig resolveProvider() {
        String provider = Optional.ofNullable(properties.provider()).orElse("openai").toLowerCase(Locale.ROOT);
        String model = Optional.ofNullable(properties.model()).filter(v -> !v.isBlank()).orElse("gpt-4o-mini");

        return switch (provider) {
            case "openrouter" -> new ProviderConfig(
                    "https://openrouter.ai/api/v1",
                    valueOrEmpty(properties.openrouterApiKey()),
                    model
            );
            case "groq" -> new ProviderConfig(
                    "https://api.groq.com/openai/v1",
                    valueOrEmpty(properties.groqApiKey()),
                    model
            );
            case "custom" -> new ProviderConfig(
                    valueOrEmpty(properties.customBaseUrl()),
                    valueOrEmpty(properties.customApiKey()),
                    model
            );
            default -> new ProviderConfig(
                    "https://api.openai.com/v1",
                    valueOrEmpty(properties.openaiApiKey()),
                    model
            );
        };
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trim(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private record ProviderConfig(String baseUrl, String apiKey, String model) {
    }
}




package com.sentria.notification;

import com.sentria.application.port.NotificationSender;
import com.sentria.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Sends notifications to a <a href="https://ntfy.sh">ntfy</a> server via HTTP POST.
 *
 * <p>The ntfy topic, server URL, and enabled flag are read from
 * {@link NotificationProperties}. When ntfy is disabled, the call is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NtfySender implements NotificationSender {

    private final HttpClient httpClient;
    private final NotificationProperties notificationProperties;

    /**
     * Posts the notification body to {@code <serverUrl>/<topic>}.
     * The {@code Title} and {@code Priority} headers are set from the notification fields.
     */
    @Override
    public void send(FormattedNotification notification) {
        NotificationProperties.Ntfy ntfy = notificationProperties.ntfy();

        if (!ntfy.enabled()) {
            log.info("ntfy is disabled, skipping notification");
            return;
        }

        String url = ntfy.serverUrl() + "/" + ntfy.topic();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Title", notification.title())
                .header("Priority", notification.priority())
                .POST(HttpRequest.BodyPublishers.ofString(notification.body(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("ntfy notification sent, status={}", response.statusCode());
        } catch (Exception e) {
            log.error("Failed to send ntfy notification", e);
        }
    }
}
package com.sentria.notification;

import com.sentria.ai.AiNotificationFormatter;
import com.sentria.domain.Finding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Composite {@link NotificationFormatter} that tries AI-enhanced formatting first
 * and silently falls back to the rule-based {@link FallbackNotificationFormatter}
 * if the AI call fails (network error, auth failure, quota exceeded, etc.).
 *
 * <p>This is the {@code @Primary} bean, so it is injected wherever
 * {@code NotificationFormatter} is requested without a qualifier.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class CompositeNotificationFormatter implements NotificationFormatter {

    private final AiNotificationFormatter aiFormatter;
    private final FallbackNotificationFormatter fallbackFormatter;

    @Override
    public FormattedNotification format(Finding finding) {
        try {
            // Attempt AI-enhanced formatting for a richer user experience.
            return aiFormatter.format(finding);
        } catch (Exception e) {
            // Any failure degrades gracefully – the user still receives a notification.
            log.info("Using fallback formatter");
            return fallbackFormatter.format(finding);
        }
    }
}

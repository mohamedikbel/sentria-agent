package com.sentria.notification;

import com.sentria.ai.AiNotificationFormatter;
import com.sentria.domain.Finding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeNotificationFormatter implements NotificationFormatter {

    private AiNotificationFormatter aiFormatter;
    private final FallbackNotificationFormatter fallbackFormatter;

    @Override
    public FormattedNotification format(Finding finding) {

        try {
            return aiFormatter.format(finding);
        } catch (Exception e) {

            log.info("Using fallback formatter");

            return fallbackFormatter.format(finding);
        }
    }
}

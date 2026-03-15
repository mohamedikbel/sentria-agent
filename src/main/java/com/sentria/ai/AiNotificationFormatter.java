package com.sentria.ai;

import com.sentria.domain.Finding;
import com.sentria.notification.FormattedNotification;
import com.sentria.notification.NotificationFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiNotificationFormatter implements NotificationFormatter {

    private final OpenAiCompatibleClient aiClient;

    public AiNotificationFormatter(OpenAiCompatibleClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public FormattedNotification format(Finding finding) {
        String prompt = """
                Rewrite this hardware alert as a short, clear mobile notification.

                Rules:
                - Use only the facts provided
                - Do not invent metrics, causes, or dates
                - Keep it concise and readable

                Finding:
                %s
                """.formatted(finding);

        String content = aiClient.complete(prompt).orElseGet(() -> "Alert: " + finding.type());

        return new FormattedNotification(
                "Sentria Alert",
                content,
                "default"
        );
    }
}
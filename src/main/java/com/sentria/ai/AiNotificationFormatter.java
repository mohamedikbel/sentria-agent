package com.sentria.ai;

import com.sentria.domain.Finding;
import com.sentria.notification.FormattedNotification;
import com.sentria.notification.NotificationFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiNotificationFormatter implements NotificationFormatter {

    private final ChatClient chatClient;

    public AiNotificationFormatter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
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

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return new FormattedNotification(
                "Sentria Alert",
                content,
                "default"
        );
    }
}
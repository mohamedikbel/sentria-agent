package com.sentria.notification;

import com.sentria.domain.Finding;
import org.springframework.stereotype.Component;

@Component
public class FallbackNotificationFormatter implements NotificationFormatter {

    @Override
    public FormattedNotification format(Finding finding) {
        String title = buildTitle(finding);
        String body = buildBody(finding);
        String priority = mapPriority(finding);

        return new FormattedNotification(title, body, priority);
    }

    private String buildTitle(Finding finding) {
        return switch (finding.type()) {
            case "BATTERY_FULLY_CHARGED" -> "Battery fully charged";
            case "SSD_WEAR_ACCELERATING" -> "SSD wear accelerating";
            case "THERMAL_ANOMALY" -> "Thermal anomaly detected";
            default -> "Sentria alert";
        };
    }

    private String buildBody(Finding finding) {
        StringBuilder body = new StringBuilder();

        body.append("What happened:\n");
        for (String fact : finding.facts()) {
            body.append("- ").append(fact).append("\n");
        }

        if (finding.likelyContributor() != null && !finding.likelyContributor().isBlank()) {
            body.append("\nLikely contributor:\n");
            body.append("- ").append(finding.likelyContributor()).append("\n");
        }

        body.append("\nConfidence:\n");
        body.append("- ").append(pretty(finding.confidence().name())).append("\n");

        body.append("\nRecommended:\n");
        for (String recommendation : finding.recommendations()) {
            body.append("- ").append(recommendation).append("\n");
        }

        return body.toString().trim();
    }

    private String mapPriority(Finding finding) {
        return switch (finding.severity()) {
            case LOW -> "low";
            case MEDIUM -> "default";
            case HIGH, CRITICAL -> "high";
        };
    }

    private String pretty(String value) {
        return value.substring(0, 1) + value.substring(1).toLowerCase();
    }
}
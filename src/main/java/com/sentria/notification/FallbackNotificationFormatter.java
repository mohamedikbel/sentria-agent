package com.sentria.notification;

import com.sentria.domain.Finding;
import org.springframework.stereotype.Component;

/**
 * Pure rule-based {@link NotificationFormatter}.
 * Does not call any external service, so it is always available as a last resort.
 * Title, body sections, and ntfy priority are derived directly from the finding data.
 */
@Component
public class FallbackNotificationFormatter implements NotificationFormatter {

    @Override
    public FormattedNotification format(Finding finding) {
        String title = buildTitle(finding);
        String body = buildBody(finding);
        String priority = mapPriority(finding);

        return new FormattedNotification(title, body, priority);
    }

    /** Maps the finding type to a short, human-readable title. */
    private String buildTitle(Finding finding) {
        return switch (finding.type()) {
            case "BATTERY_FULLY_CHARGED" -> "Battery fully charged";
            case "SSD_WEAR_ACCELERATING" -> "SSD wear accelerating";
            case "THERMAL_ANOMALY" -> "Thermal anomaly detected";
            default -> "Sentria alert";
        };
    }

    /**
     * Builds the notification body with four sections:
     * "What happened", "Likely contributor" (if present), "Confidence", and "Recommended".
     */
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

    /** Translates severity to the ntfy priority string expected by the sender. */
    private String mapPriority(Finding finding) {
        return switch (finding.severity()) {
            case LOW -> "low";
            case MEDIUM -> "default";
            case HIGH, CRITICAL -> "high";
        };
    }

    /** Capitalises only the first letter (e.g. "HIGH" → "High"). */
    private String pretty(String value) {
        return value.substring(0, 1) + value.substring(1).toLowerCase();
    }
}
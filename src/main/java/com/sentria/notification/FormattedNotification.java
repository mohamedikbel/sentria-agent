package com.sentria.notification;

/**
 * A fully formatted notification ready to be dispatched to the user.
 *
 * @param title    Short subject line shown as the notification heading.
 * @param body     Multi-line message body with facts and recommendations.
 * @param priority ntfy priority string: "low", "default" or "high".
 */
public record FormattedNotification(
        String title,
        String body,
        String priority
) {
}

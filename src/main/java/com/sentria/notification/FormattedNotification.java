package com.sentria.notification;


public record FormattedNotification(
        String title,
        String body,
        String priority
) {
}

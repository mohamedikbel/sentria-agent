package com.sentria.application.port;

import com.sentria.notification.FormattedNotification;

/**
 * Port for dispatching outbound notifications to the user.
 * The primary implementation sends messages via the ntfy push-notification service.
 */
public interface NotificationSender {

    /** Sends the given notification to the configured channel. */
    void send(FormattedNotification notification);
}

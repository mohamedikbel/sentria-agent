package com.sentria.notification;

import com.sentria.domain.Finding;

/**
 * Strategy interface for formatting a {@link Finding} into a human-readable notification.
 * Two implementations exist:
 * <ul>
 *   <li>{@link FallbackNotificationFormatter} – rule-based, always available.</li>
 *   <li>{@link CompositeNotificationFormatter} – tries AI formatting first, falls back otherwise.</li>
 * </ul>
 */
public interface NotificationFormatter {

   /** Converts the given finding into a ready-to-send notification. */
   FormattedNotification format(Finding finding);
}
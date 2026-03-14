package com.sentria.notification;

import com.sentria.domain.Finding;
import com.sentria.notification.FormattedNotification;

public interface NotificationFormatter {

   FormattedNotification format(Finding finding);
}
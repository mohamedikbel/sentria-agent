package com.sentria.bootstrap;

import com.sentria.notification.FormattedNotification;
import com.sentria.application.port.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStartupTester implements CommandLineRunner {

    private final NotificationSender notificationSender;

    @Override
    public void run(String... args) {
        log.info("Sending startup test notification");

        notificationSender.send(new FormattedNotification(
                "Sentria is active",
                "Sentria is now running correctly on your device.",
                "default"
        ));
    }
}
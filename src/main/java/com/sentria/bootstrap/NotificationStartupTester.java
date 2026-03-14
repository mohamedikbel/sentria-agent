package com.sentria.bootstrap;

import com.sentria.notification.FormattedNotification;
import com.sentria.notification.NtfySender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStartupTester implements CommandLineRunner {

    private final NtfySender ntfySender;

    @Override
    public void run(String... args) {
        log.info("Sending startup test notification");

        ntfySender.send(new FormattedNotification(
                "Sentria is active",
                "Sentria is now running correctly on your device.",
                "default"
        ));
    }
}
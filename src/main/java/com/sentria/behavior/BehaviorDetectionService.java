package com.sentria.behavior;

import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import com.sentria.application.behavior.RunningProcessProvider;
import com.sentria.application.port.BehaviorSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Detects high-level user behaviors (video editing, gaming) by inspecting the
 * list of currently running processes.
 *
 * <p>On each tick the service compares the live process list against known
 * process-name sets. It opens a new {@link BehaviorSession} when a behavior starts
 * and closes the existing session when the triggering process disappears.
 */
@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class BehaviorDetectionService {

    /** Process names that indicate active video-editing work. */
    private static final Set<String> VIDEO_EDITING_PROCESSES = Set.of(
            "premiere.exe",
            "adobe media encoder.exe",
            "davinciresolve.exe",
            "afterfx.exe"
    );

    /** Process names that indicate active gaming. */
    private static final Set<String> GAMING_PROCESSES = Set.of(
            "steam.exe",
            "epicgameslauncher.exe",
            "battle.net.exe"
    );

    private final BehaviorSessionStore behaviorSessionRepository;
    private final RunningProcessProvider runningProcessProvider;

    /** Scheduled detection pass – runs on the global monitoring interval. */
    @Scheduled(fixedRateString = "${monitoring.interval-seconds}000")
    public void detectBehaviors() {
        List<String> processNames = runningProcessProvider.getNormalizedProcessNames();

        boolean videoEditingDetected = processNames.stream().anyMatch(VIDEO_EDITING_PROCESSES::contains);
        boolean gamingDetected = processNames.stream().anyMatch(GAMING_PROCESSES::contains);

        handleSession(
                BehaviorSessionType.VIDEO_EDITING,
                videoEditingDetected,
                firstMatchingProcess(processNames, VIDEO_EDITING_PROCESSES)
        );

        handleSession(
                BehaviorSessionType.GAMING,
                gamingDetected,
                firstMatchingProcess(processNames, GAMING_PROCESSES)
        );
    }

    /**
     * Opens a new session if the behavior is detected and no session is open,
     * or closes the open session if the behavior is no longer detected.
     */
    private void handleSession(BehaviorSessionType type, boolean detected, String context) {
        BehaviorSession openSession = behaviorSessionRepository.findOpenSessionByType(type);

        if (detected && openSession == null) {
            BehaviorSession newSession = new BehaviorSession(
                    UUID.randomUUID().toString(),
                    "local-device",
                    type,
                    Instant.now(),
                    null,
                    context
            );

            behaviorSessionRepository.save(newSession);
            log.info("Started behavior session {} ({})", type, context);
            return;
        }

        if (!detected && openSession != null) {
            behaviorSessionRepository.closeSession(openSession.id(), Instant.now());
            log.info("Closed behavior session {}", type);
        }
    }

    /** Returns the first process name that matches any of the given candidates, or null. */
    private String firstMatchingProcess(List<String> processNames, Set<String> candidates) {
        return processNames.stream()
                .filter(candidates::contains)
                .findFirst()
                .orElse(null);
    }
}
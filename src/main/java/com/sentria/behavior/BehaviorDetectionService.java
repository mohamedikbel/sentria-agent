package com.sentria.behavior;


import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import com.sentria.repository.BehaviorSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class BehaviorDetectionService {

    private static final Set<String> VIDEO_EDITING_PROCESSES = Set.of(
            "premiere.exe",
            "adobe media encoder.exe",
            "davinciresolve.exe",
            "afterfx.exe"
    );

    private static final Set<String> GAMING_PROCESSES = Set.of(
            "steam.exe",
            "epicgameslauncher.exe",
            "battle.net.exe"
    );

    private final BehaviorSessionRepository behaviorSessionRepository;

    private final SystemInfo systemInfo = new SystemInfo();

    @Scheduled(
            fixedRateString = "${monitoring.interval-seconds}000")
    public void detectBehaviors() {
        OperatingSystem os = systemInfo.getOperatingSystem();

        List<String> processNames = os.getProcesses().stream()
                .map(OSProcess::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toList());

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

    private String firstMatchingProcess(List<String> processNames, Set<String> candidates) {
        return processNames.stream()
                .filter(candidates::contains)
                .findFirst()
                .orElse(null);
    }
}
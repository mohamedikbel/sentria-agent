package com.sentria.application.behavior;

import com.sentria.application.port.ProcessSnapshotStore;
import com.sentria.config.MonitoringProperties;
import com.sentria.domain.ProcessSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class TopProcessCaptureService {

    private static final int TOP_LIMIT = 5;

    private final ProcessSnapshotStore processSnapshotStore;
    private final MonitoringProperties monitoringProperties;
    private final SystemInfo systemInfo = new SystemInfo();

    @Scheduled(fixedRateString = "#{${monitoring.process-interval-seconds} * 1000}")
    public void captureTopProcesses() {
        OperatingSystem os = systemInfo.getOperatingSystem();
        List<OSProcess> processes = os.getProcesses(
                process -> true,
                OperatingSystem.ProcessSorting.CPU_DESC,
                TOP_LIMIT
        );

        Instant now = Instant.now();
        List<ProcessSnapshot> snapshots = processes.stream()
                .map(p -> new ProcessSnapshot(
                        "local-device",
                        p.getName() != null ? p.getName() : "unknown",
                        p.getProcessID(),
                        Math.max(0.0, p.getProcessCpuLoadCumulative() * 100.0),
                        p.getResidentSetSize() / (1024.0 * 1024.0),
                        safeCommandLine(p.getCommandLine()),
                        now
                ))
                .toList();

        if (snapshots.isEmpty()) {
            return;
        }

        processSnapshotStore.saveAll(snapshots);
        log.debug("Captured {} top process snapshot(s) every {}s", snapshots.size(), monitoringProperties.processIntervalSeconds());
    }

    private String safeCommandLine(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return null;
        }
        return commandLine.length() > 500 ? commandLine.substring(0, 500) : commandLine;
    }
}



package com.sentria.infrastructure.monitoring;

import com.sentria.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class SmartctlBinaryLocator {

    private final StorageProperties storageProperties;

    public Optional<String> findCommand() {
        StorageProperties.Smart smart = storageProperties.smart();

        if (smart == null || !smart.enabled()) {
            return Optional.empty();
        }

        if (smart.binaryPath() != null && !smart.binaryPath().isBlank()) {
            Path explicit = Path.of(smart.binaryPath().trim());
            if (Files.isRegularFile(explicit)) {
                return Optional.of(explicit.toAbsolutePath().toString());
            }
        }

        for (Path candidate : platformCandidates()) {
            if (candidate.isAbsolute() && Files.isRegularFile(candidate)) {
                return Optional.of(candidate.toString());
            }

            if (!candidate.isAbsolute()) {
                Path localCandidate = candidate.toAbsolutePath();
                if (Files.isRegularFile(localCandidate)) {
                    return Optional.of(localCandidate.toString());
                }
            }
        }

        return probePathCommand();
    }

    private Optional<String> probePathCommand() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String command = os.contains("win") ? "smartctl.exe" : "smartctl";

        try {
            Process process = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }

            return process.exitValue() == 0 ? Optional.of(command) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<Path> platformCandidates() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> candidates = new ArrayList<>();

        if (os.contains("win")) {
            candidates.add(Path.of("smartctl.exe"));
            candidates.add(Path.of("bin", "smartctl.exe"));
            candidates.add(Path.of("tools", "smartmontools", "bin", "smartctl.exe"));
            candidates.add(Path.of("C:\\Program Files\\smartmontools\\bin\\smartctl.exe"));
            return candidates;
        }

        if (os.contains("mac")) {
            candidates.add(Path.of("smartctl"));
            candidates.add(Path.of("/opt/homebrew/sbin/smartctl"));
            candidates.add(Path.of("/usr/local/sbin/smartctl"));
            candidates.add(Path.of("/usr/sbin/smartctl"));
            return candidates;
        }

        candidates.add(Path.of("smartctl"));
        candidates.add(Path.of("/usr/sbin/smartctl"));
        candidates.add(Path.of("/usr/bin/smartctl"));
        candidates.add(Path.of("/sbin/smartctl"));
        return candidates;
    }
}




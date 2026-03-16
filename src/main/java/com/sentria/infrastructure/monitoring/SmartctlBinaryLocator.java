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

    private static final String SMARTCTL_BINARY_WINDOWS = "smartctl.exe";
    private static final String SMARTCTL_BINARY_UNIX    = "smartctl";

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
        String command = os.contains("win") ? SMARTCTL_BINARY_WINDOWS : SMARTCTL_BINARY_UNIX;

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
            candidates.add(Path.of(SMARTCTL_BINARY_WINDOWS));
            candidates.add(Path.of("bin", SMARTCTL_BINARY_WINDOWS));
            candidates.add(Path.of("tools", "smartmontools", "bin", SMARTCTL_BINARY_WINDOWS));
            candidates.add(Path.of("C:\\Program Files\\smartmontools\\bin\\" + SMARTCTL_BINARY_WINDOWS));
            return candidates;
        }

        if (os.contains("mac")) {
            candidates.add(Path.of(SMARTCTL_BINARY_UNIX));
            candidates.add(Path.of("/opt/homebrew/sbin/" + SMARTCTL_BINARY_UNIX));
            candidates.add(Path.of("/usr/local/sbin/" + SMARTCTL_BINARY_UNIX));
            candidates.add(Path.of("/usr/sbin/" + SMARTCTL_BINARY_UNIX));
            return candidates;
        }

        candidates.add(Path.of(SMARTCTL_BINARY_UNIX));
        candidates.add(Path.of("/usr/sbin/" + SMARTCTL_BINARY_UNIX));
        candidates.add(Path.of("/usr/bin/" + SMARTCTL_BINARY_UNIX));
        candidates.add(Path.of("/sbin/" + SMARTCTL_BINARY_UNIX));
        return candidates;
    }
}

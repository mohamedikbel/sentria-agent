package com.sentria.infrastructure.monitoring;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentria.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@DependsOn("flyway")

@RequiredArgsConstructor
public class SmartctlRunner {

    private final StorageProperties storageProperties;
    private final SmartctlBinaryLocator binaryLocator;
    private final ObjectMapper objectMapper;

    public boolean isAvailable() {
        StorageProperties.Smart smart = storageProperties.smart();
        return smart != null && smart.enabled() && binaryLocator.findCommand().isPresent();
    }

    public List<SmartctlDevice> discoverDevices() {
        StorageProperties.Smart smart = storageProperties.smart();
        Optional<String> command = binaryLocator.findCommand();

        if (smart == null || !smart.enabled() || command.isEmpty() || !smart.discoveryEnabled()) {
            return List.of();
        }

        try {
            Process process = new ProcessBuilder(command.get(), "--scan-open")
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exit = process.waitFor();
            if (exit != 0 && output.isBlank()) {
                return List.of();
            }

            return Arrays.stream(output.split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(this::parseScanLine)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("smartctl device discovery failed", e);
            return List.of();
        }
    }

    public JsonNode readDeviceJson(SmartctlDevice device) {
        Optional<String> command = binaryLocator.findCommand();

        if (command.isEmpty()) {
            return null;
        }

        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(command.get());
        fullCommand.add("-a");
        fullCommand.add("-j");

        if (device.type() != null && !device.type().isBlank() && !"auto".equalsIgnoreCase(device.type())) {
            fullCommand.add("-d");
            fullCommand.add(device.type());
        }

        fullCommand.add(device.path());

        try {
            Process process = new ProcessBuilder(fullCommand)
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exitCode = process.waitFor();
            log.debug("smartctl finished with exit code {} for {}", exitCode, device.path());

            if (output.isBlank()) {
                return null;
            }

            return objectMapper.readTree(output);
        } catch (Exception e) {
            log.warn("Failed to execute smartctl for {}", device.path(), e);
            return null;
        }
    }

    private SmartctlDevice parseScanLine(String line) {
        Matcher typed = Pattern.compile("^(\\S+)\\s+-d\\s+(\\S+)\\s+#.*$").matcher(line);
        if (typed.matches()) {
            return new SmartctlDevice(typed.group(1), typed.group(2));
        }

        Matcher generic = Pattern.compile("^(\\S+)\\s+#.*$").matcher(line);
        if (generic.matches()) {
            return new SmartctlDevice(generic.group(1), "auto");
        }

        return null;
    }
}


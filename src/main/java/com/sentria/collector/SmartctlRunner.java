package com.sentria.collector;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentria.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@DependsOn("flyway")

@RequiredArgsConstructor
public class SmartctlRunner {

    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode readDeviceJson() {
        StorageProperties.Smartctl smartctl = storageProperties.smartctl();

        if (smartctl == null || !smartctl.enabled()) {
            log.info("smartctl is disabled");
            return null;
        }

        List<String> command = List.of(
                smartctl.command(),
                "-a",
                "-j",
                smartctl.device()
        );

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            try (InputStream inputStream = process.getInputStream()) {
                JsonNode root = objectMapper.readTree(inputStream);
                int exitCode = process.waitFor();

                log.info("smartctl finished with exit code {}", exitCode);
                return root;
            }
        } catch (Exception e) {
            log.error("Failed to execute smartctl", e);
            return null;
        }
    }
}

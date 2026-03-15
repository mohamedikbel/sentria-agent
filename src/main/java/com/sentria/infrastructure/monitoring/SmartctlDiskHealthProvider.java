package com.sentria.infrastructure.monitoring;

import com.sentria.application.monitoring.DiskHealthMode;
import com.sentria.application.monitoring.DiskHealthProvider;
import com.sentria.application.monitoring.DiskHealthSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SmartctlDiskHealthProvider implements DiskHealthProvider {

    private final SmartctlRunner smartctlRunner;

    @Override
    public DiskHealthMode mode() {
        return smartctlRunner.isAvailable() ? DiskHealthMode.SMART_FULL : DiskHealthMode.UNAVAILABLE;
    }

    @Override
    public List<DiskHealthSnapshot> collect() {
        if (!smartctlRunner.isAvailable()) {
            return List.of();
        }

        List<DiskHealthSnapshot> snapshots = new ArrayList<>();

        for (SmartctlDevice device : smartctlRunner.discoverDevices()) {
            JsonNode root = smartctlRunner.readDeviceJson(device);
            if (root == null || root.isEmpty()) {
                continue;
            }

            snapshots.add(mapSnapshot(device, root));
        }

        return snapshots;
    }

    private DiskHealthSnapshot mapSnapshot(SmartctlDevice device, JsonNode root) {
        return new DiskHealthSnapshot(
                device.path(),
                text(root, "model_name"),
                text(root, "serial_number"),
                root.path("user_capacity").path("bytes").isNumber()
                        ? root.path("user_capacity").path("bytes").asLong()
                        : null,
                extractHealth(root),
                extractTemperature(root),
                extractWrittenGb(root),
                null,
                null,
                Instant.now(),
                Map.of("source", "smartctl", "deviceType", device.type())
        );
    }

    private String text(JsonNode root, String field) {
        return root.path(field).isMissingNode() ? null : root.path(field).asText(null);
    }

    private Double extractHealth(JsonNode root) {
        JsonNode nvme = root.path("nvme_smart_health_information_log");
        if (nvme.has("percentage_used")) {
            return Math.max(0.0, 100.0 - nvme.path("percentage_used").asDouble());
        }

        JsonNode ataTable = root.path("ata_smart_attributes").path("table");
        if (ataTable.isArray()) {
            for (JsonNode row : ataTable) {
                String name = row.path("name").asText("");
                if ("Wear_Leveling_Count".equalsIgnoreCase(name)
                        || "Media_Wearout_Indicator".equalsIgnoreCase(name)
                        || "SSD_Life_Left".equalsIgnoreCase(name)
                        || "Percent_Lifetime_Remain".equalsIgnoreCase(name)) {
                    if (row.path("value").isNumber()) {
                        return row.path("value").asDouble();
                    }
                    if (row.path("raw").path("value").isNumber()) {
                        return row.path("raw").path("value").asDouble();
                    }
                }
            }
        }

        return null;
    }

    private Double extractTemperature(JsonNode root) {
        JsonNode nvme = root.path("nvme_smart_health_information_log");
        if (nvme.has("temperature")) {
            return nvme.path("temperature").asDouble();
        }

        JsonNode temp = root.path("temperature").path("current");
        if (temp.isNumber()) {
            return temp.asDouble();
        }

        return null;
    }

    private Double extractWrittenGb(JsonNode root) {
        JsonNode nvme = root.path("nvme_smart_health_information_log");
        if (nvme.has("data_units_written")) {
            double units = nvme.path("data_units_written").asDouble();
            return units * 512000.0 / 1_000_000_000.0;
        }

        JsonNode ataTable = root.path("ata_smart_attributes").path("table");
        if (ataTable.isArray()) {
            for (JsonNode row : ataTable) {
                String name = row.path("name").asText("");
                if ("Total_LBAs_Written".equalsIgnoreCase(name)) {
                    if (row.path("raw").path("value").isNumber()) {
                        return row.path("raw").path("value").asDouble() * 512.0 / 1_000_000_000.0;
                    }
                }
            }
        }

        return null;
    }
}




package com.sentria.finding;

import com.sentria.domain.Confidence;
import com.sentria.domain.Finding;
import com.sentria.domain.FindingType;
import com.sentria.domain.Severity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Factory that builds well-formed {@link Finding} objects for each anomaly type.
 * Centralising construction here keeps the business logic (service classes)
 * clean and ensures every finding has consistent facts and recommendations.
 */
@Component
public class FindingFactory {

    /**
     * Creates a {@link FindingType#BATTERY_FULLY_CHARGED} finding.
     * Severity is LOW because this is informational – the battery is healthy,
     * but staying at 100 % unnecessarily adds charge-cycle stress.
     */
    public Finding batteryFullyCharged() {
        return new Finding(
                UUID.randomUUID().toString(),
                FindingType.BATTERY_FULLY_CHARGED,
                Severity.LOW,
                Confidence.HIGH,
                List.of("Battery reached 100% while still plugged in"),
                null,
                List.of("You can unplug the charger if you want to reduce unnecessary time at full charge"),
                Instant.now()
        );
    }

    /**
     * Creates a {@link FindingType#SSD_WEAR_ACCELERATING} finding.
     *
     * @param startHealth       SSD health at the beginning of the analysis window (%).
     * @param endHealth         SSD health at the end of the analysis window (%).
     * @param days              Number of days in the analysis window.
     * @param likelyContributor Free-text description of the probable cause, or null.
     */
    public Finding ssdWearAccelerating(double startHealth, double endHealth, int days, String likelyContributor) {
        List<String> facts = new ArrayList<>();
        facts.add("SSD health dropped from %.1f%% to %.1f%% in %d days".formatted(startHealth, endHealth, days));

        List<String> recommendations = new ArrayList<>();
        recommendations.add("Back up important files immediately");
        recommendations.add("Consider planning SSD replacement soon");

        // Extra caution when the drive is nearly exhausted.
        if (endHealth <= 15) {
            recommendations.add("Avoid heavy write workloads on this drive when possible");
        }

        return new Finding(
                UUID.randomUUID().toString(),
                FindingType.SSD_WEAR_ACCELERATING,
                Severity.HIGH,
                Confidence.MEDIUM,
                facts,
                likelyContributor,
                recommendations,
                Instant.now()
        );
    }
}
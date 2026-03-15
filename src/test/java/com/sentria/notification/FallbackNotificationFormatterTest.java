package com.sentria.notification;

import com.sentria.domain.Confidence;
import com.sentria.domain.Finding;
import com.sentria.domain.FindingType;
import com.sentria.domain.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FallbackNotificationFormatter}.
 * No Spring context – pure logic tests.
 */
class FallbackNotificationFormatterTest {

    private FallbackNotificationFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new FallbackNotificationFormatter();
    }

    // ── title mapping ────────────────────────────────────────────────────────

    @Test
    void format_batteryFinding_titleIsBatteryFullyCharged() {
        Finding f = batteryFinding();
        assertThat(formatter.format(f).title()).isEqualTo("Battery fully charged");
    }

    @Test
    void format_ssdFinding_titleIsSsdWearAccelerating() {
        Finding f = ssdFinding(null);
        assertThat(formatter.format(f).title()).isEqualTo("SSD wear accelerating");
    }

    @Test
    void format_unknownType_fallsBackToGenericTitle() {
        Finding f = findingWithType("SOME_UNKNOWN_TYPE", Severity.MEDIUM);
        assertThat(formatter.format(f).title()).isEqualTo("Sentria alert");
    }

    // ── body content ─────────────────────────────────────────────────────────

    @Test
    void format_bodyContainsFacts() {
        Finding f = batteryFinding();
        assertThat(formatter.format(f).body()).contains("Battery reached 100%");
    }

    @Test
    void format_bodyContainsRecommendations() {
        Finding f = batteryFinding();
        assertThat(formatter.format(f).body()).contains("Recommended:");
    }

    @Test
    void format_bodyContainsConfidenceSection() {
        Finding f = batteryFinding();
        assertThat(formatter.format(f).body()).contains("Confidence:");
    }

    @Test
    void format_bodyContainsContributor_whenPresent() {
        Finding f = ssdFinding("Heavy writes during gaming");
        assertThat(formatter.format(f).body()).contains("Heavy writes during gaming");
    }

    @Test
    void format_bodyOmitsContributorSection_whenNull() {
        Finding f = ssdFinding(null);
        assertThat(formatter.format(f).body()).doesNotContain("Likely contributor:");
    }

    @Test
    void format_bodyOmitsContributorSection_whenBlank() {
        Finding f = new Finding("id", FindingType.SSD_WEAR_ACCELERATING, Severity.HIGH,
                Confidence.MEDIUM, List.of("fact"), "   ", List.of("rec"), Instant.now());
        assertThat(formatter.format(f).body()).doesNotContain("Likely contributor:");
    }

    // ── priority mapping ─────────────────────────────────────────────────────

    @Test
    void format_lowSeverity_priorityIsLow() {
        Finding f = findingWithType(FindingType.BATTERY_FULLY_CHARGED, Severity.LOW);
        assertThat(formatter.format(f).priority()).isEqualTo("low");
    }

    @Test
    void format_mediumSeverity_priorityIsDefault() {
        Finding f = findingWithType("ANY", Severity.MEDIUM);
        assertThat(formatter.format(f).priority()).isEqualTo("default");
    }

    @Test
    void format_highSeverity_priorityIsHigh() {
        Finding f = findingWithType(FindingType.SSD_WEAR_ACCELERATING, Severity.HIGH);
        assertThat(formatter.format(f).priority()).isEqualTo("high");
    }

    @Test
    void format_criticalSeverity_priorityIsHigh() {
        Finding f = findingWithType("THERMAL_ANOMALY", Severity.CRITICAL);
        assertThat(formatter.format(f).priority()).isEqualTo("high");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Finding batteryFinding() {
        return new Finding(
                "test-id",
                FindingType.BATTERY_FULLY_CHARGED,
                Severity.LOW,
                Confidence.HIGH,
                List.of("Battery reached 100% while still plugged in"),
                null,
                List.of("You can unplug the charger"),
                Instant.now()
        );
    }

    private Finding ssdFinding(String contributor) {
        return new Finding(
                "test-id",
                FindingType.SSD_WEAR_ACCELERATING,
                Severity.HIGH,
                Confidence.MEDIUM,
                List.of("SSD health dropped from 80.0% to 70.0% in 14 days"),
                contributor,
                List.of("Back up important files immediately"),
                Instant.now()
        );
    }

    private Finding findingWithType(String type, Severity severity) {
        return new Finding(
                "test-id", type, severity, Confidence.MEDIUM,
                List.of("a fact"), null, List.of("a recommendation"), Instant.now()
        );
    }
}


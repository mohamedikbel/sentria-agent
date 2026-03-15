package com.sentria.finding;

import com.sentria.domain.Confidence;
import com.sentria.domain.Finding;
import com.sentria.domain.FindingType;
import com.sentria.domain.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FindingFactory}.
 * No Spring context required – the factory has no dependencies.
 */
class FindingFactoryTest {

    private FindingFactory factory;

    @BeforeEach
    void setUp() {
        factory = new FindingFactory();
    }

    // ── batteryFullyCharged ──────────────────────────────────────────────────

    @Test
    void batteryFullyCharged_returnsCorrectType() {
        Finding f = factory.batteryFullyCharged();
        assertThat(f.type()).isEqualTo(FindingType.BATTERY_FULLY_CHARGED);
    }

    @Test
    void batteryFullyCharged_severityIsLow() {
        assertThat(factory.batteryFullyCharged().severity()).isEqualTo(Severity.LOW);
    }

    @Test
    void batteryFullyCharged_confidenceIsHigh() {
        assertThat(factory.batteryFullyCharged().confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void batteryFullyCharged_hasAtLeastOneFact() {
        assertThat(factory.batteryFullyCharged().facts()).isNotEmpty();
    }

    @Test
    void batteryFullyCharged_hasAtLeastOneRecommendation() {
        assertThat(factory.batteryFullyCharged().recommendations()).isNotEmpty();
    }

    @Test
    void batteryFullyCharged_contributorIsNull() {
        // Battery finding has no external contributor.
        assertThat(factory.batteryFullyCharged().likelyContributor()).isNull();
    }

    @Test
    void batteryFullyCharged_generatesUniqueIds() {
        String id1 = factory.batteryFullyCharged().id();
        String id2 = factory.batteryFullyCharged().id();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void batteryFullyCharged_createdAtIsSet() {
        assertThat(factory.batteryFullyCharged().createdAt()).isNotNull();
    }

    // ── ssdWearAccelerating ──────────────────────────────────────────────────

    @Test
    void ssdWearAccelerating_returnsCorrectType() {
        Finding f = factory.ssdWearAccelerating(80, 70, 14, null);
        assertThat(f.type()).isEqualTo(FindingType.SSD_WEAR_ACCELERATING);
    }

    @Test
    void ssdWearAccelerating_severityIsHigh() {
        assertThat(factory.ssdWearAccelerating(80, 70, 14, null).severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void ssdWearAccelerating_confidenceIsMedium() {
        assertThat(factory.ssdWearAccelerating(80, 70, 14, null).confidence()).isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void ssdWearAccelerating_factMentionsHealthValues() {
        Finding f = factory.ssdWearAccelerating(80.0, 70.0, 14, null);
        String fact = f.facts().get(0);
        // Locale-agnostic: decimal separator may be '.' or ',' depending on the JVM locale.
        assertThat(fact).contains("80").contains("70").contains("14");
    }

    @Test
    void ssdWearAccelerating_contributorIsIncludedWhenProvided() {
        String contributor = "Video editing sessions";
        Finding f = factory.ssdWearAccelerating(90, 80, 14, contributor);
        assertThat(f.likelyContributor()).isEqualTo(contributor);
    }

    @Test
    void ssdWearAccelerating_contributorNullWhenNotProvided() {
        Finding f = factory.ssdWearAccelerating(90, 80, 14, null);
        assertThat(f.likelyContributor()).isNull();
    }

    @Test
    void ssdWearAccelerating_criticalHealthAddsExtraRecommendation() {
        // endHealth ≤ 15 should trigger the extra "avoid heavy write workloads" recommendation.
        Finding f = factory.ssdWearAccelerating(20, 10, 14, null);
        assertThat(f.recommendations()).anyMatch(r -> r.toLowerCase().contains("heavy write"));
    }

    @Test
    void ssdWearAccelerating_normalHealthDoesNotAddExtraRecommendation() {
        // endHealth > 15 → only the two standard recommendations.
        Finding f = factory.ssdWearAccelerating(90, 80, 14, null);
        assertThat(f.recommendations()).hasSize(2);
    }

    @Test
    void ssdWearAccelerating_hasAtLeastOneRecommendation() {
        assertThat(factory.ssdWearAccelerating(80, 70, 14, null).recommendations()).isNotEmpty();
    }
}



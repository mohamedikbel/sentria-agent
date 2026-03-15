package com.sentria.application.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SsdMonitoringStatus}.
 * Verifies state transitions between available and unavailable.
 */
class SsdMonitoringStatusTest {

    private SsdMonitoringStatus status;

    @BeforeEach
    void setUp() {
        status = new SsdMonitoringStatus();
    }

    @Test
    void initialState_isUnavailable() {
        assertThat(status.isAvailable()).isFalse();
    }

    @Test
    void initialState_reasonIsNotBlank() {
        assertThat(status.getReason()).isNotBlank();
    }

    @Test
    void markAvailable_setsAvailableToTrue() {
        status.markAvailable("SMART data found");
        assertThat(status.isAvailable()).isTrue();
    }

    @Test
    void markAvailable_updatesReason() {
        status.markAvailable("SMART data found");
        assertThat(status.getReason()).isEqualTo("SMART data found");
    }

    @Test
    void markUnavailable_setsAvailableToFalse() {
        status.markAvailable("ok");
        status.markUnavailable("smartctl not found");
        assertThat(status.isAvailable()).isFalse();
    }

    @Test
    void markUnavailable_updatesReason() {
        status.markUnavailable("no SSD detected");
        assertThat(status.getReason()).isEqualTo("no SSD detected");
    }

    @Test
    void toggleAvailability_reflectsLastCall() {
        status.markAvailable("first");
        status.markUnavailable("second");
        status.markAvailable("third");
        assertThat(status.isAvailable()).isTrue();
        assertThat(status.getReason()).isEqualTo("third");
    }
}


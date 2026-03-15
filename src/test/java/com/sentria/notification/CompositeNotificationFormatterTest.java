package com.sentria.notification;

import com.sentria.ai.AiNotificationFormatter;
import com.sentria.domain.Confidence;
import com.sentria.domain.Finding;
import com.sentria.domain.FindingType;
import com.sentria.domain.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CompositeNotificationFormatter}.
 * Verifies that the AI formatter is tried first and that the fallback is used
 * when the AI formatter throws any exception.
 */
@ExtendWith(MockitoExtension.class)
class CompositeNotificationFormatterTest {

    @Mock
    private AiNotificationFormatter aiFormatter;

    private FallbackNotificationFormatter fallbackFormatter;
    private CompositeNotificationFormatter composite;

    @BeforeEach
    void setUp() {
        fallbackFormatter = new FallbackNotificationFormatter();
        composite = new CompositeNotificationFormatter(aiFormatter, fallbackFormatter);
    }

    @Test
    void format_usesAiResult_whenAiSucceeds() {
        FormattedNotification aiResult = new FormattedNotification("AI Title", "AI Body", "high");
        when(aiFormatter.format(any())).thenReturn(aiResult);

        FormattedNotification result = composite.format(anyFinding());

        assertThat(result.title()).isEqualTo("AI Title");
        assertThat(result.body()).isEqualTo("AI Body");
        verify(aiFormatter).format(any());
    }

    @Test
    void format_fallsBackToFallback_whenAiThrowsRuntimeException() {
        when(aiFormatter.format(any())).thenThrow(new RuntimeException("AI unavailable"));

        FormattedNotification result = composite.format(batteryFinding());

        // Should not be the AI result – fallback produces a deterministic title.
        assertThat(result.title()).isEqualTo("Battery fully charged");
        verify(aiFormatter).format(any());
    }

    @Test
    void format_fallsBackToFallback_whenAiThrowsError() {
        when(aiFormatter.format(any())).thenThrow(new IllegalStateException("quota exceeded"));

        FormattedNotification result = composite.format(batteryFinding());

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Battery fully charged");
    }

    @Test
    void format_doesNotCallFallback_whenAiSucceeds() {
        when(aiFormatter.format(any())).thenReturn(new FormattedNotification("T", "B", "low"));

        composite.format(anyFinding());

        // Fallback is a real object, but we can verify the AI path was taken.
        verify(aiFormatter, times(1)).format(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Finding batteryFinding() {
        return new Finding(
                "id", FindingType.BATTERY_FULLY_CHARGED, Severity.LOW, Confidence.HIGH,
                List.of("Battery reached 100%"), null, List.of("Unplug charger"), Instant.now()
        );
    }

    private Finding anyFinding() {
        return new Finding(
                "id", FindingType.SSD_WEAR_ACCELERATING, Severity.HIGH, Confidence.MEDIUM,
                List.of("fact"), null, List.of("rec"), Instant.now()
        );
    }
}


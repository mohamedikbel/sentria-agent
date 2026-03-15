package com.sentria.context;

import com.sentria.application.port.BehaviorSessionStore;
import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BehaviorCorrelationService}.
 */
@ExtendWith(MockitoExtension.class)
class BehaviorCorrelationServiceTest {

    @Mock private BehaviorSessionStore store;
    private BehaviorCorrelationService service;

    private final Instant since = Instant.now().minusSeconds(1_209_600);

    @BeforeEach
    void setUp() {
        service = new BehaviorCorrelationService(store);
    }

    @Test
    void returnsNull_whenNoHeavyWriteSessions() {
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of());

        assertThat(service.findLikelySsdWearContributor(since)).isNull();
    }

    @Test
    void returnsNoDominantPattern_whenNoOverlapWithVideoOrGaming() {
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.HEAVY_WRITE_ACTIVITY,
                        Instant.now().minusSeconds(100), Instant.now().minusSeconds(50))));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.VIDEO_EDITING), any()))
                .thenReturn(List.of());
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.GAMING), any()))
                .thenReturn(List.of());

        assertThat(service.findLikelySsdWearContributor(since))
                .contains("no dominant workload pattern");
    }

    @Test
    void returnsVideoEditing_whenVideoOverlapsMore() {
        Instant s = Instant.now().minusSeconds(3600);
        Instant e = Instant.now().minusSeconds(1800);

        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.HEAVY_WRITE_ACTIVITY, s, e)));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.VIDEO_EDITING), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.VIDEO_EDITING,
                        s.minusSeconds(60), e.plusSeconds(60))));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.GAMING), any()))
                .thenReturn(List.of());

        assertThat(service.findLikelySsdWearContributor(since))
                .contains("video editing");
    }

    @Test
    void returnsGaming_whenGamingOverlapsMore() {
        Instant s = Instant.now().minusSeconds(3600);
        Instant e = Instant.now().minusSeconds(1800);

        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.HEAVY_WRITE_ACTIVITY, s, e)));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.VIDEO_EDITING), any()))
                .thenReturn(List.of());
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.GAMING), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.GAMING,
                        s.minusSeconds(60), e.plusSeconds(60))));

        assertThat(service.findLikelySsdWearContributor(since)).contains("gaming");
    }

    @Test
    void nonOverlappingSessions_doNotCount() {
        Instant hwEnd   = Instant.now().minusSeconds(7200);
        Instant hwStart = hwEnd.minusSeconds(3600);
        Instant vidStart = Instant.now().minusSeconds(1800);
        Instant vidEnd   = Instant.now().minusSeconds(900);

        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.HEAVY_WRITE_ACTIVITY, hwStart, hwEnd)));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.VIDEO_EDITING), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.VIDEO_EDITING, vidStart, vidEnd)));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.GAMING), any()))
                .thenReturn(List.of());

        assertThat(service.findLikelySsdWearContributor(since))
                .contains("no dominant workload pattern");
    }

    private BehaviorSession sess(BehaviorSessionType type, Instant start, Instant end) {
        return new BehaviorSession("id", "local-device", type, start, end, null);
    }
}

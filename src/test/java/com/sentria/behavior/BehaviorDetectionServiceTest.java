package com.sentria.behavior;

import com.sentria.application.behavior.RunningProcessProvider;
import com.sentria.application.port.BehaviorSessionStore;
import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BehaviorDetectionService}.
 */
@ExtendWith(MockitoExtension.class)
class BehaviorDetectionServiceTest {

    @Mock private BehaviorSessionStore sessionStore;
    @Mock private RunningProcessProvider processProvider;

    private BehaviorDetectionService service;

    @BeforeEach
    void setUp() {
        service = new BehaviorDetectionService(sessionStore, processProvider);
    }

    @Test
    void detectBehaviors_opensVideoEditingSession_whenPremiereRunning() {
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("premiere.exe"));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.VIDEO_EDITING)).thenReturn(null);
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.GAMING)).thenReturn(null);

        service.detectBehaviors();

        ArgumentCaptor<BehaviorSession> cap = ArgumentCaptor.forClass(BehaviorSession.class);
        verify(sessionStore, atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues())
                .anyMatch(s -> s.sessionType() == BehaviorSessionType.VIDEO_EDITING);
    }

    @Test
    void detectBehaviors_opensGamingSession_whenSteamRunning() {
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("steam.exe"));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.VIDEO_EDITING)).thenReturn(null);
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.GAMING)).thenReturn(null);

        service.detectBehaviors();

        ArgumentCaptor<BehaviorSession> cap = ArgumentCaptor.forClass(BehaviorSession.class);
        verify(sessionStore, atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues())
                .anyMatch(s -> s.sessionType() == BehaviorSessionType.GAMING);
    }

    @Test
    void detectBehaviors_doesNotOpenSession_whenAlreadyOpen() {
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("premiere.exe"));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.VIDEO_EDITING))
                .thenReturn(openSession(BehaviorSessionType.VIDEO_EDITING));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.GAMING)).thenReturn(null);

        service.detectBehaviors();

        verify(sessionStore, never()).save(any());
    }

    @Test
    void detectBehaviors_closesSession_whenProcessNoLongerRunning() {
        BehaviorSession open = openSession(BehaviorSessionType.VIDEO_EDITING);
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("chrome.exe"));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.VIDEO_EDITING)).thenReturn(open);
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.GAMING)).thenReturn(null);

        service.detectBehaviors();

        verify(sessionStore).closeSession(eq(open.id()), any(Instant.class));
    }

    @Test
    void detectBehaviors_doesNothingForUnknownProcesses() {
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("notepad.exe"));
        when(sessionStore.findOpenSessionByType(any())).thenReturn(null);

        service.detectBehaviors();

        verify(sessionStore, never()).save(any());
    }

    private BehaviorSession openSession(BehaviorSessionType type) {
        return new BehaviorSession("test-id", "local-device", type,
                Instant.now().minusSeconds(60), null, "process.exe");
    }
}

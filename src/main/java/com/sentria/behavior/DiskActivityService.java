package com.sentria.behavior;


import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.repository.BehaviorSessionRepository;
import com.sentria.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class DiskActivityService {

    private final MetricSnapshotRepository metricRepository;
    private final BehaviorSessionRepository behaviorRepository;

    private static final double HEAVY_WRITE_THRESHOLD_GB = 5.0;

    @Scheduled(
            fixedRateString = "${monitoring.interval-seconds}000")    public void detectHeavyWriteActivity() {

        List<MetricSnapshot> snapshots =
                metricRepository.findRecentSnapshots(MetricType.SSD_BYTES_WRITTEN_GB, 2);

        if (snapshots.size() < 2) {
            return;
        }

        MetricSnapshot newest = snapshots.get(0);
        MetricSnapshot previous = snapshots.get(1);

        double delta = newest.value() - previous.value();

        if (delta < HEAVY_WRITE_THRESHOLD_GB) {
            closeSessionIfOpen();
            return;
        }

        BehaviorSession open =
                behaviorRepository.findOpenSessionByType(BehaviorSessionType.HEAVY_WRITE_ACTIVITY);

        if (open != null) {
            return;
        }

        BehaviorSession session = new BehaviorSession(
                UUID.randomUUID().toString(),
                "local-device",
                BehaviorSessionType.HEAVY_WRITE_ACTIVITY,
                Instant.now(),
                null,
                "Disk writes +" + delta + "GB"
        );

        behaviorRepository.save(session);

        log.info("Heavy disk write session started (+{} GB)", delta);
    }

    private void closeSessionIfOpen() {

        BehaviorSession open =
                behaviorRepository.findOpenSessionByType(BehaviorSessionType.HEAVY_WRITE_ACTIVITY);

        if (open == null) {
            return;
        }

        behaviorRepository.closeSession(open.id(), Instant.now());

        log.info("Heavy disk write session closed");
    }
}

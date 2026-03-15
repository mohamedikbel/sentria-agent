package com.sentria.application.port;

import com.sentria.domain.ProcessSnapshot;

import java.time.Instant;
import java.util.List;

public interface ProcessSnapshotStore {

    void saveAll(List<ProcessSnapshot> snapshots);

    List<ProcessSnapshot> findTopByCpuSince(Instant since, int limit);

    List<ProcessSnapshot> findMostUsedSince(Instant since, int limit);
}



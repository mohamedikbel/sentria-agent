package com.sentria.infrastructure.monitoring;

import com.sentria.application.monitoring.DiskHealthMode;
import com.sentria.application.monitoring.DiskHealthProvider;
import com.sentria.application.monitoring.DiskHealthSnapshot;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OshiDiskHealthProvider implements DiskHealthProvider {

    private final SystemInfo systemInfo = new SystemInfo();

    @Override
    public DiskHealthMode mode() {
        return DiskHealthMode.BASIC_OSHI;
    }

    @Override
    public List<DiskHealthSnapshot> collect() {
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        List<DiskHealthSnapshot> snapshots = new ArrayList<>();

        for (HWDiskStore disk : hardware.getDiskStores()) {
            disk.updateAttributes();
            double writeBytes = disk.getWriteBytes();
            long writes = disk.getWrites();

            Double bytesWrittenGb = null;
            if (writeBytes >= 0) {
                bytesWrittenGb = writeBytes / 1_000_000_000.0;
            } else if (writes >= 0) {
                // Fallback approximation when OSHI cannot expose byte counters.
                bytesWrittenGb = (writes * 512.0) / 1_000_000_000.0;
            }

            snapshots.add(new DiskHealthSnapshot(
                    disk.getName(),
                    disk.getModel(),
                    disk.getSerial(),
                    disk.getSize(),
                    null,
                    null,
                    bytesWrittenGb,
                    disk.getReads(),
                    writes,
                    Instant.now(),
                    Map.of("source", "oshi")
            ));
        }

        return snapshots;
    }
}






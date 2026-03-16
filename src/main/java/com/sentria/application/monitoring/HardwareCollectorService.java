package com.sentria.application.monitoring;

import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.PowerSource;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@DependsOn("flyway")
// Hardware collector plugin: returns snapshots only; orchestration/persistence is centralized.
public class HardwareCollectorService implements MetricCollectorPlugin {

    private static final String DEVICE_ID = "local-device";

    private final SystemInfo systemInfo = new SystemInfo();

    private long[] previousCpuTicks;
    private long previousNetRxBytes = -1L;
    private long previousNetTxBytes = -1L;
    private Instant previousNetSampleAt;

    @Override
    public String collectorName() {
        return "hardware";
    }

    @Override
    public List<MetricSnapshot> collect(Instant collectedAt) {
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        OperatingSystem os = systemInfo.getOperatingSystem();
        List<MetricSnapshot> snapshots = new ArrayList<>();

        collectCpu(hardware, snapshots, collectedAt);
        collectCpuTemperature(hardware, snapshots, collectedAt);
        collectMemory(hardware, snapshots, collectedAt);
        collectBattery(hardware, snapshots, collectedAt);
        collectStorage(os, snapshots, collectedAt);
        collectNetwork(hardware, snapshots, collectedAt);

        return snapshots;
    }

    private void collectCpu(HardwareAbstractionLayer hardware, List<MetricSnapshot> snapshots, Instant collectedAt) {
        CentralProcessor cpu = hardware.getProcessor();

        if (previousCpuTicks == null) {
            previousCpuTicks = cpu.getSystemCpuLoadTicks();
            log.info("Initialized CPU ticks baseline");
            return;
        }

        double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0;
        previousCpuTicks = cpu.getSystemCpuLoadTicks();

        MetricSnapshot snapshot = new MetricSnapshot(
                DEVICE_ID,
                MetricType.CPU_USAGE_PERCENT,
                cpuLoad,
                collectedAt
        );
        snapshots.add(snapshot);
        log.info("CPU usage: {}%", Math.round(snapshot.value() * 10.0) / 10.0);
    }

    private void collectMemory(HardwareAbstractionLayer hardware, List<MetricSnapshot> snapshots, Instant collectedAt) {
        GlobalMemory memory = hardware.getMemory();

        double usedPercent =
                ((double) (memory.getTotal() - memory.getAvailable()) / memory.getTotal()) * 100.0;

        MetricSnapshot snapshot = new MetricSnapshot(
                DEVICE_ID,
                MetricType.RAM_USAGE_PERCENT,
                usedPercent,
                collectedAt
        );
        snapshots.add(snapshot);
        log.info("RAM usage: {}%", Math.round(snapshot.value() * 10.0) / 10.0);
    }

    private void collectCpuTemperature(HardwareAbstractionLayer hardware, List<MetricSnapshot> snapshots, Instant collectedAt) {
        double cpuTemperature = hardware.getSensors().getCpuTemperature();
        if (cpuTemperature <= 0.0) {
            return;
        }

        MetricSnapshot snapshot = new MetricSnapshot(
                DEVICE_ID,
                MetricType.CPU_TEMPERATURE_C,
                cpuTemperature,
                collectedAt
        );
        snapshots.add(snapshot);
        log.info("CPU temperature: {} C", Math.round(cpuTemperature * 10.0) / 10.0);
    }


    private void collectBattery(HardwareAbstractionLayer hardware, List<MetricSnapshot> snapshots, Instant collectedAt) {
        List<PowerSource> powerSources = hardware.getPowerSources();
        if (powerSources == null || powerSources.isEmpty()) {
            log.debug("No battery detected");
            return;
        }

        PowerSource battery = powerSources.getFirst();

        double batteryPercent = resolveBatteryPercent(battery);
        double charging = battery.isCharging() ? 1.0 : 0.0;

        MetricSnapshot batterySnapshot = new MetricSnapshot(
                DEVICE_ID,
                MetricType.BATTERY_PERCENT,
                batteryPercent,
                collectedAt
        );
        snapshots.add(batterySnapshot);

        MetricSnapshot chargingSnapshot = new MetricSnapshot(
                DEVICE_ID,
                MetricType.BATTERY_CHARGING,
                charging,
                collectedAt
        );
        snapshots.add(chargingSnapshot);

        double remainingMinutes = battery.getTimeRemainingEstimated() > 0
                ? battery.getTimeRemainingEstimated() / 60.0
                : -1.0;
        if (remainingMinutes > 0.0) {
            MetricSnapshot remainingSnapshot = new MetricSnapshot(
                    DEVICE_ID,
                    MetricType.BATTERY_TIME_REMAINING_MIN,
                    remainingMinutes,
                    collectedAt
            );
            snapshots.add(remainingSnapshot);
        }

        log.info(
                "Battery saved: {}%, charging={}, currentCapacity={}, maxCapacity={}, remainingCapacityPercent={}",
                batteryPercent,
                battery.isCharging(),
                battery.getCurrentCapacity(),
                battery.getMaxCapacity(),
                battery.getRemainingCapacityPercent()
        );
    }

    private double resolveBatteryPercent(PowerSource battery) {
        int currentCapacity = battery.getCurrentCapacity();
        int maxCapacity = battery.getMaxCapacity();

        if (maxCapacity > 0 && currentCapacity >= 0) {
            double percent = (currentCapacity * 100.0) / maxCapacity;
            if (percent >= 0.0 && percent <= 100.0) {
                return percent;
            }
        }

        double percent = battery.getRemainingCapacityPercent() * 100.0;

        return Math.max(0.0, Math.min(percent, 100.0));
    }

    private void collectStorage(OperatingSystem os, List<MetricSnapshot> snapshots, Instant collectedAt) {
        FileSystem fileSystem = os.getFileSystem();
        List<OSFileStore> stores = fileSystem.getFileStores();
        if (stores == null || stores.isEmpty()) {
            return;
        }

        long total = 0L;
        long usable = 0L;
        for (OSFileStore store : stores) {
            total += Math.max(0L, store.getTotalSpace());
            usable += Math.max(0L, store.getUsableSpace());
        }

        if (total <= 0L) {
            return;
        }

        double usedPercent = ((double) (total - usable) / total) * 100.0;
        double freeGb = usable / 1_000_000_000.0;

        snapshots.add(new MetricSnapshot(
                DEVICE_ID,
                MetricType.STORAGE_USED_PERCENT,
                Math.max(0.0, Math.min(100.0, usedPercent)),
                collectedAt
        ));

        snapshots.add(new MetricSnapshot(
                DEVICE_ID,
                MetricType.STORAGE_FREE_GB,
                Math.max(0.0, freeGb),
                collectedAt
        ));
    }

    private void collectNetwork(HardwareAbstractionLayer hardware, List<MetricSnapshot> snapshots, Instant collectedAt) {
        long totalRx = 0L;
        long totalTx = 0L;

        for (NetworkIF networkIF : hardware.getNetworkIFs()) {
            networkIF.updateAttributes();
            totalRx += Math.max(0L, networkIF.getBytesRecv());
            totalTx += Math.max(0L, networkIF.getBytesSent());
        }

        if (previousNetSampleAt == null || previousNetRxBytes < 0 || previousNetTxBytes < 0) {
            previousNetSampleAt = collectedAt;
            previousNetRxBytes = totalRx;
            previousNetTxBytes = totalTx;
            return;
        }

        double seconds = Math.max(1.0, java.time.Duration.between(previousNetSampleAt, collectedAt).toMillis() / 1000.0);
        double downMbps = Math.max(0.0, (totalRx - previousNetRxBytes) / 1_000_000.0 / seconds);
        double upMbps = Math.max(0.0, (totalTx - previousNetTxBytes) / 1_000_000.0 / seconds);

        previousNetSampleAt = collectedAt;
        previousNetRxBytes = totalRx;
        previousNetTxBytes = totalTx;

        snapshots.add(new MetricSnapshot(
                DEVICE_ID,
                MetricType.NETWORK_DOWNLOAD_MBPS,
                downMbps,
                collectedAt
        ));

        snapshots.add(new MetricSnapshot(
                DEVICE_ID,
                MetricType.NETWORK_UPLOAD_MBPS,
                upMbps,
                collectedAt
        ));
    }
}










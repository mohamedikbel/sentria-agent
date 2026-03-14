package com.sentria.collector;

import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.repository.MetricSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PowerSource;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@DependsOn("flyway")
public class HardwareCollectorService {

    private final SystemInfo systemInfo = new SystemInfo();
    private final MetricSnapshotRepository metricSnapshotRepository;

    private long[] previousCpuTicks;

    public HardwareCollectorService(MetricSnapshotRepository metricSnapshotRepository) {
        this.metricSnapshotRepository = metricSnapshotRepository;
    }

    @Scheduled(
            fixedRateString = "${monitoring.interval-seconds}000")
    public void collectMetrics() {
        HardwareAbstractionLayer hardware = systemInfo.getHardware();

        collectCpu(hardware);
        collectMemory(hardware);
        collectBattery(hardware);
    }

    private void collectCpu(HardwareAbstractionLayer hardware) {
        CentralProcessor cpu = hardware.getProcessor();

        if (previousCpuTicks == null) {
            previousCpuTicks = cpu.getSystemCpuLoadTicks();
            log.info("Initialized CPU ticks baseline");
            return;
        }

        double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0;
        previousCpuTicks = cpu.getSystemCpuLoadTicks();

        MetricSnapshot snapshot = new MetricSnapshot(
                "local-device",
                MetricType.CPU_USAGE_PERCENT,
                cpuLoad,
                Instant.now()
        );
        metricSnapshotRepository.save(snapshot);
        log.info("CPU usage: {}%", Math.round(snapshot.value() * 10.0) / 10.0);
    }

    private void collectMemory(HardwareAbstractionLayer hardware) {
        GlobalMemory memory = hardware.getMemory();

        double usedPercent =
                ((double) (memory.getTotal() - memory.getAvailable()) / memory.getTotal()) * 100.0;

        MetricSnapshot snapshot = new MetricSnapshot(
                "local-device",
                MetricType.RAM_USAGE_PERCENT,
                usedPercent,
                Instant.now()
        );
        metricSnapshotRepository.save(snapshot);
        log.info("RAM usage: {}%", Math.round(snapshot.value() * 10.0) / 10.0);
    }


    private void collectBattery(HardwareAbstractionLayer hardware) {
        List<PowerSource> powerSources = hardware.getPowerSources();
        if (powerSources == null || powerSources.isEmpty()) {
            log.debug("No battery detected");
            return;
        }

        PowerSource battery = powerSources.get(0);

        double batteryPercent = resolveBatteryPercent(battery);
        double charging = battery.isCharging() ? 1.0 : 0.0;

        MetricSnapshot batterySnapshot = new MetricSnapshot(
                "local-device",
                MetricType.BATTERY_PERCENT,
                batteryPercent,
                Instant.now()
        );
        metricSnapshotRepository.save(batterySnapshot);

        MetricSnapshot chargingSnapshot = new MetricSnapshot(
                "local-device",
                MetricType.BATTERY_CHARGING,
                charging,
                Instant.now()
        );
        metricSnapshotRepository.save(chargingSnapshot);

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

        if (percent < 0.0) {
            return 0.0;
        }
        if (percent > 100.0) {
            return 100.0;
        }
        return percent;
    }
}
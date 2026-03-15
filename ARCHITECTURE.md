# Sentria Architecture (Simple and Plug-in Friendly)

## Core idea

Sentria now uses a **collector plug-in pipeline**:

1. Each collector implements `MetricCollectorPlugin`.
2. `MetricCollectionOrchestrator` runs all collectors on schedule.
3. Collectors return `MetricSnapshot` objects.
4. The orchestrator persists snapshots through `MetricSnapshotStore`.
5. Detection services read metrics and create findings/notifications.

This keeps the flow simple: **collect -> store -> detect -> notify**.

## Main contracts

- `com.sentria.application.monitoring.MetricCollectorPlugin`
  - one interface to plug new metrics
- `com.sentria.application.monitoring.MetricCollectionOrchestrator`
  - single scheduler and persistence point
- `com.sentria.application.port.*`
  - persistence interfaces (`MetricSnapshotStore`, `FindingStore`, `BehaviorSessionStore`)
- `com.sentria.application.port.NotificationSender`
  - notification transport abstraction

## Existing collectors

- `HardwareCollectorService` (CPU, RAM, battery)
- `SsdCollectorService` (SMART/OSHI SSD data)

Both are now plug-ins discovered automatically by Spring.

## How to add a new metric (quick recipe)

1. Create a new `@Service` implementing `MetricCollectorPlugin`.
2. In `collect(Instant collectedAt)`, build and return `MetricSnapshot` list.
3. Use a new or existing `MetricType` value.
4. That's it: the orchestrator runs it automatically.

### Minimal example

```java
@Service
@DependsOn("flyway")
public class NetworkCollectorService implements MetricCollectorPlugin {

    @Override
    public String collectorName() {
        return "network";
    }

    @Override
    public List<MetricSnapshot> collect(Instant collectedAt) {
        double usage = 42.0;
        return List.of(new MetricSnapshot(
                "local-device",
                MetricType.RAM_USAGE_PERCENT,
                usage,
                collectedAt
        ));
    }
}
```

## Why this is easier to maintain

- One schedule point for all metric collectors.
- One persistence path for all snapshots.
- Collectors are isolated and small.
- Adding/removing collectors does not require changing orchestration code.
- Business logic in findings/behavior stays unchanged.



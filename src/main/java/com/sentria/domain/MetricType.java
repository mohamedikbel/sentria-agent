package com.sentria.domain;

/** All metric types that Sentria collectors can produce. */
public enum MetricType {

    // ── SSD ──────────────────────────────────────────────────────────────────
    /** Remaining SSD health as a percentage (100 % = brand-new). */
    SSD_HEALTH_PERCENT,
    /** Cumulative total bytes written to the SSD, in gigabytes. */
    SSD_BYTES_WRITTEN_GB,

    // ── CPU ──────────────────────────────────────────────────────────────────
    /** CPU package temperature in degrees Celsius. */
    CPU_TEMPERATURE_C,
    /** Overall CPU utilisation, 0–100 %. */
    CPU_USAGE_PERCENT,

    // ── Battery ───────────────────────────────────────────────────────────────
    /** Battery charge level, 0–100 %. */
    BATTERY_PERCENT,
    /** 1.0 if the battery is currently charging, 0.0 otherwise. */
    BATTERY_CHARGING,
    /** Estimated time remaining on battery, in minutes. */
    BATTERY_TIME_REMAINING_MIN,

    // ── Storage ───────────────────────────────────────────────────────────────
    /** Percentage of total storage capacity currently in use. */
    STORAGE_USED_PERCENT,
    /** Free storage space in gigabytes. */
    STORAGE_FREE_GB,

    // ── Network ───────────────────────────────────────────────────────────────
    /** Current network download throughput in megabits per second. */
    NETWORK_DOWNLOAD_MBPS,
    /** Current network upload throughput in megabits per second. */
    NETWORK_UPLOAD_MBPS,

    // ── Memory ───────────────────────────────────────────────────────────────
    /** RAM utilisation, 0–100 %. */
    RAM_USAGE_PERCENT

}
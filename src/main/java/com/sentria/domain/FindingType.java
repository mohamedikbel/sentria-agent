package com.sentria.domain;

/**
 * String constants for every finding type produced by Sentria.
 * Using string constants (instead of an enum) keeps findings forward-compatible:
 * older persisted rows remain valid if new types are added later.
 */
public final class FindingType {

    /** Battery reached 100 % while the charger was still connected. */
    public static final String BATTERY_FULLY_CHARGED = "BATTERY_FULLY_CHARGED";

    /** SSD health percentage dropped more than expected over the analysis window. */
    public static final String SSD_WEAR_ACCELERATING = "SSD_WEAR_ACCELERATING";

    // Utility class – not instantiable.
    private FindingType() {
    }
}
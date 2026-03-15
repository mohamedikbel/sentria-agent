package com.sentria.domain;

/** Indicates how confident Sentria is in a particular finding. */
public enum Confidence {

    /** Analysis is based on limited data; treat the finding as a hint. */
    LOW,

    /** Analysis is based on several corroborating data points. */
    MEDIUM,

    /** Analysis is based on strong, consistent evidence. */
    HIGH

}
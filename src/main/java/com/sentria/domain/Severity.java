package com.sentria.domain;

/** How serious a detected finding is. Used to drive notification priority. */
public enum Severity {

    /** Informational – no immediate action required. */
    LOW,

    /** Noteworthy – the user should be aware. */
    MEDIUM,

    /** Significant – action is recommended soon. */
    HIGH,

    /** Urgent – immediate action required to avoid data loss or damage. */
    CRITICAL
}

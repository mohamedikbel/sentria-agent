package com.sentria.domain;

/** Category of user-activity session that Sentria tracks. */
public enum BehaviorSessionType {

    /** User is running a video-editing application (Premiere, DaVinci Resolve, etc.). */
    VIDEO_EDITING,

    /** User is running a game launcher or game client. */
    GAMING,

    /** Device is idle – no significant workload detected. */
    IDLE,

    /** Unusually large amount of data is being written to the SSD. */
    HEAVY_WRITE_ACTIVITY

}
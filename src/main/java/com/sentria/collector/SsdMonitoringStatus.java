package com.sentria.collector;


import org.springframework.stereotype.Component;

@Component
public class SsdMonitoringStatus {

    private volatile boolean available = false;
    private volatile String reason = "Not checked yet";

    public boolean isAvailable() {
        return available;
    }

    public String getReason() {
        return reason;
    }

    public void markAvailable(String reason) {
        this.available = true;
        this.reason = reason;
    }

    public void markUnavailable(String reason) {
        this.available = false;
        this.reason = reason;
    }
}

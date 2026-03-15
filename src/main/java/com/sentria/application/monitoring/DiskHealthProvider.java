package com.sentria.application.monitoring;

import java.util.List;

public interface DiskHealthProvider {

    DiskHealthMode mode();

    List<DiskHealthSnapshot> collect();
}




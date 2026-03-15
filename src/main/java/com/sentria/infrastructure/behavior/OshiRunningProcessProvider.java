package com.sentria.infrastructure.behavior;

import com.sentria.application.behavior.RunningProcessProvider;

import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.List;
import java.util.Locale;

@Component
public class OshiRunningProcessProvider implements RunningProcessProvider {

    private final SystemInfo systemInfo = new SystemInfo();

    @Override
    public List<String> getNormalizedProcessNames() {
        OperatingSystem os = systemInfo.getOperatingSystem();

        return os.getProcesses().stream()
                .map(OSProcess::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }
}




package com.sentria;

import com.sentria.bootstrap.GuidedSetupCli;
import com.sentria.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan("com.sentria.config")

public class SentriaAgentApplication {

    public static void main(String[] args) {
        if (Arrays.stream(args).anyMatch("--setup"::equalsIgnoreCase)) {
            GuidedSetupCli.run(args);
            return;
        }

        SpringApplication.run(SentriaAgentApplication.class, args);
    }

}

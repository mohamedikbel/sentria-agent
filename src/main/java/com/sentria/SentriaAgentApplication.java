package com.sentria;

import com.sentria.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan("com.sentria.config")

public class SentriaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentriaAgentApplication.class, args);
    }

}

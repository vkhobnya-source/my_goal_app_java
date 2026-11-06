package com.goaltracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GoalsTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoalsTrackerApplication.class, args);
    }
}

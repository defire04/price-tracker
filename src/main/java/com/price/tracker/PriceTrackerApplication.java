package com.price.tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PriceTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceTrackerApplication.class, args);
    }

}

package com.wealthora.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WealthoraServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WealthoraServerApplication.class, args);
    }
}

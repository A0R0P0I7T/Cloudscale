package com.cloudscale.rollout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RolloutServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RolloutServiceApplication.class, args);
    }
}

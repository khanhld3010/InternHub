package org.example.internservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class InternAndProgramServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InternAndProgramServiceApplication.class, args);
    }
}

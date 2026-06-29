package com.shrirang.distributed_promptforge.intelligence_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.mayur.distributed_promptforge.intelligence_service", "com.mayur.distributed_promptforge.common_lib"})
@EnableFeignClients
@EnableScheduling
public class IntelligenceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntelligenceServiceApplication.class, args);
	}

}

package com.campuspulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CampuspulseBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CampuspulseBackendApplication.class, args);
	}

}

package com.substring.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class HelpDeskBackedApplication {

	public static void main(String[] args) {
		SpringApplication.run(HelpDeskBackedApplication.class, args);
	}

}

package com.mockdata.zipfit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ZipfitApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZipfitApplication.class, args);
	}

}

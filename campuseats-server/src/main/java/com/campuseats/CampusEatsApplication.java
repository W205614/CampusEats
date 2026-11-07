package com.campuseats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampusEatsApplication {
  public static void main(String[] args) {
    SpringApplication.run(CampusEatsApplication.class, args);
  }
}

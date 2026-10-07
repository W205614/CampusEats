package com.sky;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkyApplication {
  public static void main(String[] args) {
    SpringApplication.run(SkyApplication.class, args);
  }
}

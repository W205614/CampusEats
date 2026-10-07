package com.sky.infra;

import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class StaticFiles implements WebMvcConfigurer {
  private final String root;

  public StaticFiles(@Value("${campus.uploads}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize().toUri().toString();
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/uploads/**").addResourceLocations(root).setCachePeriod(86400);
  }
}

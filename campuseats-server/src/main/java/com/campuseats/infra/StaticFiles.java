package com.campuseats.infra;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class StaticFiles implements WebMvcConfigurer {
  // ProductImageController serves the same stable URL from LOCAL or COS.
  // A filesystem catch-all must not bypass object visibility and metadata checks.
}

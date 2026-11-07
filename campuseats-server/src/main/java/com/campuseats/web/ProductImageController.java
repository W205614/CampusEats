package com.campuseats.web;

import com.campuseats.storage.StoredImages;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductImageController {
  private final StoredImages images;

  public ProductImageController(StoredImages images) {
    this.images = images;
  }

  @GetMapping(value = "/uploads/{id:[a-f0-9-]{36}}.png", produces = MediaType.IMAGE_PNG_VALUE)
  public void image(@PathVariable String id, HttpServletResponse response) throws IOException {
    try (var content = images.open(id, false)) {
      response.setContentType(MediaType.IMAGE_PNG_VALUE);
      response.setContentLengthLong(content.size());
      response.setHeader("Cache-Control", "public, max-age=86400, immutable");
      response.setHeader("X-Content-Type-Options", "nosniff");
      content.transferTo(response.getOutputStream());
    }
  }
}

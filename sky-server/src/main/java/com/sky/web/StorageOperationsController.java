package com.sky.web;

import com.sky.storage.StoredImages;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/storage")
public class StorageOperationsController {
  private final StoredImages images;

  public StorageOperationsController(StoredImages images) {
    this.images = images;
  }

  @GetMapping("/images")
  public List<Map<String, Object>> review(@RequestParam(defaultValue = "UNKNOWN") String state) {
    return images.review(state);
  }

  @PostMapping("/images/{id}/verify")
  public void verify(@PathVariable String id) throws IOException {
    images.verify(id);
  }
}

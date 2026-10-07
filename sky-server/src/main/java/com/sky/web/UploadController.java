package com.sky.web;

import com.sky.business.UploadService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UploadController {
  private final UploadService uploads;

  public UploadController(UploadService uploads) {
    this.uploads = uploads;
  }

  @PostMapping("/api/v1/admin/uploads")
  public Map<String, Object> upload(@RequestParam MultipartFile file) {
    return Map.of("url", uploads.upload(file));
  }
}

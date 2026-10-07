package com.sky.web;

import com.sky.business.TaskRunner;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class TaskController {
  private final TaskRunner tasks;

  public TaskController(TaskRunner tasks) {
    this.tasks = tasks;
  }

  @GetMapping("/api/v1/admin/tasks/{type}")
  public List<Map<String, Object>> list(@PathVariable String type) {
    return tasks.tasks(type);
  }

  @PostMapping("/api/v1/admin/tasks/{type}/{id}/retry")
  public void retry(@PathVariable String type, @PathVariable long id) {
    tasks.retry(type, id);
  }
}

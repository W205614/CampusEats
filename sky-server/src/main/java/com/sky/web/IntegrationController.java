package com.sky.web;

import com.sky.integration.IntegrationReadiness;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/integrations")
public class IntegrationController {
  public record Acceptance(@NotBlank @Size(max = 500) String evidenceReference) {}

  private final IntegrationReadiness readiness;

  public IntegrationController(IntegrationReadiness readiness) {
    this.readiness = readiness;
  }

  @GetMapping
  public java.util.List<IntegrationReadiness.State> states() {
    return readiness.states();
  }

  @PostMapping("/{provider}/acceptance")
  public void accept(@PathVariable String provider, @Valid @RequestBody Acceptance input) {
    readiness.accept(provider, input.evidenceReference());
  }
}

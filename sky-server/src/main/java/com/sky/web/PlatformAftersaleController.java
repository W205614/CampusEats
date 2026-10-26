package com.sky.web;

import com.sky.api.Commands;
import com.sky.platform.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "campus.legacy-api",
    havingValue = "false",
    matchIfMissing = true)
@RequestMapping("/api/v1")
public class PlatformAftersaleController {
  private final AftersaleService aftersale;
  private final EvidenceService evidence;
  private final PlatformJobs jobs;

  public PlatformAftersaleController(AftersaleService aftersale, EvidenceService evidence, PlatformJobs jobs) {
    this.aftersale = aftersale;
    this.evidence = evidence;
    this.jobs = jobs;
  }

  @PostMapping("/user/orders/{id}/aftersales")
  public Object apply(@PathVariable long id, @Valid @RequestBody PlatformCommands.Aftersale input) {
    return Map.of("id", aftersale.apply(id, input));
  }

  @GetMapping("/user/aftersales")
  public Object userAftersales(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return aftersale.list(null, false, query);
  }

  @GetMapping("/merchant/stores/{storeId}/aftersales")
  public Object merchantAftersales(
      @PathVariable long storeId,
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return aftersale.list(storeId, false, query);
  }

  @GetMapping("/platform/aftersales")
  public Object platformAftersales(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return aftersale.list(null, true, query);
  }

  @PostMapping("/merchant/aftersales/{id}/decision")
  public void decide(@PathVariable long id, @Valid @RequestBody PlatformCommands.Decision input) {
    aftersale.decide(id, input, false);
  }

  @PostMapping("/platform/aftersales/{id}/decision")
  public void rule(@PathVariable long id, @Valid @RequestBody PlatformCommands.Decision input) {
    aftersale.decide(id, input, true);
  }

  @PostMapping("/user/aftersales/{id}/appeal")
  public void appeal(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    aftersale.appeal(id, input.version());
  }

  @PostMapping("/user/aftersales/{id}/withdraw")
  public void withdraw(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    aftersale.withdraw(id, input.version());
  }

  @PostMapping("/user/orders/{id}/evidence")
  public Object uploadEvidence(@PathVariable long id, @RequestParam MultipartFile file)
      throws java.io.IOException {
    return Map.of("id", evidence.upload(id, file));
  }

  @GetMapping(value = "/evidence/{id}", produces = MediaType.IMAGE_PNG_VALUE)
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
      description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(
          mediaType = MediaType.IMAGE_PNG_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary")))
  public void evidence(@PathVariable String id, jakarta.servlet.http.HttpServletResponse response)
      throws java.io.IOException {
    try (var read = evidence.open(id)) {
      response.setContentType(MediaType.IMAGE_PNG_VALUE);
      response.setContentLengthLong(read.size());
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("X-Content-Type-Options", "nosniff");
      read.transferTo(response.getOutputStream());
    }
  }

  @GetMapping("/platform/refunds")
  public List<PlatformViews.RefundResponse> refunds(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return PlatformViewFactory.refunds(jobs.refunds(null, query));
  }

  @GetMapping("/merchant/stores/{storeId}/refunds")
  public List<PlatformViews.RefundResponse> storeRefunds(
      @PathVariable long storeId,
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return PlatformViewFactory.refunds(jobs.refunds(storeId, query));
  }

  @PostMapping("/merchant/refunds/{id}/retry")
  public void refundRetry(@PathVariable long id) {
    jobs.retry(id);
  }
}

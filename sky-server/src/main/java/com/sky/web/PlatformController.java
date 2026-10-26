package com.sky.web;

import com.sky.business.UploadService;
import com.sky.api.Commands;
import com.sky.platform.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "campus.legacy-api",
    havingValue = "false",
    matchIfMissing = true)
@RequestMapping("/api/v1")
public class PlatformController {
  private final DirectoryService directory;
  private final PlatformScope scope;
  private final StoreCatalog catalog;
  private final DiscoveryService discovery;
  private final UploadService uploads;
  private final DiscoveryPages discoveryPages;

  public PlatformController(DirectoryService directory, PlatformScope scope, StoreCatalog catalog, DiscoveryService discovery, UploadService uploads, DiscoveryPages discoveryPages) {
    this.directory = directory;
    this.scope = scope;
    this.catalog = catalog;
    this.discovery = discovery;
    this.uploads = uploads;
    this.discoveryPages = discoveryPages;
  }

  @GetMapping("/user/campuses")
  public Object campuses() {
    return directory.campuses(false);
  }

  @GetMapping("/user/stores")
  public Object stores(
      @RequestParam(required = false) Long campusId, @RequestParam(required = false) String query) {
    return directory.stores(campusId, query, false);
  }

  @GetMapping("/user/stores/page")
  public PlatformViews.StorePageResponse storePage(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Boolean open,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return PlatformViewFactory.stores(discoveryPages.stores(campusId, query, open, cursor, limit, false));
  }

  @GetMapping("/merchant/stores/page")
  public PlatformViews.StorePageResponse merchantStorePage(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Boolean open,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return PlatformViewFactory.stores(discoveryPages.stores(campusId, query, open, cursor, limit, true));
  }

  @GetMapping("/user/stores/{id}")
  public PlatformViews.StoreResponse storeDetail(@PathVariable long id) {
    return PlatformViewFactory.store(discoveryPages.store(id));
  }

  @GetMapping("/user/search/page")
  public PlatformViews.SearchPageResponse searchPage(
      @RequestParam long campusId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Boolean open,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return PlatformViewFactory.search(discoveryPages.search(campusId, query, categoryId, open, cursor, limit));
  }

  @GetMapping("/merchant/stores")
  public Object adminStores() {
    return directory.stores(null, null, true);
  }

  @GetMapping("/user/campuses/{campusId}/buildings")
  public Object buildings(@PathVariable long campusId) {
    return directory.buildings(campusId, false);
  }

  @GetMapping("/user/stores/{storeId}/categories")
  public Object categories(@PathVariable long storeId) {
    return catalog.categories(storeId, false);
  }

  @GetMapping("/merchant/stores/{storeId}/categories")
  public Object adminCategories(@PathVariable long storeId) {
    return catalog.categories(storeId, true);
  }

  @PostMapping("/merchant/stores/{storeId}/categories")
  public Object category(@PathVariable long storeId, @Valid @RequestBody Commands.Category input) {
    return Map.of("id", catalog.category(storeId, null, input));
  }

  @PutMapping("/merchant/stores/{storeId}/categories/{id}")
  public void categoryUpdate(
      @PathVariable long storeId,
      @PathVariable long id,
      @Valid @RequestBody Commands.Category input) {
    catalog.category(storeId, id, input);
  }

  @GetMapping("/user/stores/{storeId}/products")
  public Object products(
      @PathVariable long storeId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long categoryId) {
    return catalog.products(storeId, false, query, categoryId);
  }

  @GetMapping("/merchant/stores/{storeId}/products")
  public Object adminProducts(@PathVariable long storeId) {
    return catalog.products(storeId, true, null, null);
  }

  @PostMapping("/merchant/stores/{storeId}/products/{type}")
  public Object product(
      @PathVariable long storeId,
      @PathVariable String type,
      @Valid @RequestBody Commands.Product input) {
    return Map.of("id", catalog.save(storeId, type, null, input));
  }

  @PutMapping("/merchant/stores/{storeId}/products/{type}/{id}")
  public void productUpdate(
      @PathVariable long storeId,
      @PathVariable String type,
      @PathVariable long id,
      @Valid @RequestBody Commands.Product input) {
    catalog.save(storeId, type, id, input);
  }

  @GetMapping("/merchant/stores/{storeId}/quotas")
  public Object quotas(@PathVariable long storeId, @RequestParam LocalDate date) {
    return catalog.quotas(storeId, date);
  }

  @PutMapping("/merchant/stores/{storeId}/quotas")
  public void quota(
      @PathVariable long storeId,
      @RequestParam LocalDate date,
      @Valid @RequestBody Commands.Quota input) {
    catalog.quota(storeId, date, input);
  }

  @PostMapping("/merchant/stores/{storeId}/uploads")
  public Object productUpload(@PathVariable long storeId, @RequestParam MultipartFile file) {
    scope.merchant(storeId, true);
    return Map.of("url", uploads.uploadProduct(file));
  }

  @GetMapping("/user/search")
  public Object search(
      @RequestParam long campusId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Boolean open) {
    return discovery.search(campusId, query, categoryId, open);
  }

  @GetMapping("/user/favorites")
  public Object favorites() {
    return discovery.favorites();
  }

  @PostMapping("/user/stores/{storeId}/favorites/{type}/{id}")
  public void favorite(
      @PathVariable long storeId, @PathVariable String type, @PathVariable long id) {
    discovery.favorite(storeId, type, id, true);
  }

  @DeleteMapping("/user/stores/{storeId}/favorites/{type}/{id}")
  public void unfavorite(
      @PathVariable long storeId, @PathVariable String type, @PathVariable long id) {
    discovery.favorite(storeId, type, id, false);
  }
}

package com.sky.web;

import com.sky.api.Commands.*;
import com.sky.api.Views;
import com.sky.business.*;
import com.sky.infra.ViewFactory;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CatalogController {
  private final CatalogService catalog;
  private final EmployeeService employees;
  private final ReportService reports;

  public CatalogController(
      CatalogService catalog, EmployeeService employees, ReportService reports) {
    this.catalog = catalog;
    this.employees = employees;
    this.reports = reports;
  }

  @GetMapping("/api/v1/user/menu/categories")
  public List<Views.CategoryView> categories() {
    return catalog.categories(false).stream().map(ViewFactory::category).toList();
  }

  @GetMapping("/api/v1/user/menu/items")
  public List<Views.ProductView> menu(@RequestParam long categoryId, @RequestParam String type) {
    return catalog.menu(categoryId, type).stream().map(ViewFactory::product).toList();
  }

  @GetMapping("/api/v1/admin/categories")
  public List<Views.CategoryView> adminCategories() {
    return catalog.categories(true).stream().map(ViewFactory::category).toList();
  }

  @PostMapping("/api/v1/admin/categories")
  public Map<String, Object> category(@Valid @RequestBody Category input) {
    return Map.of("id", catalog.category(null, input));
  }

  @PutMapping("/api/v1/admin/categories/{id}")
  public void updateCategory(@PathVariable long id, @Valid @RequestBody Category input) {
    catalog.category(id, input);
  }

  @DeleteMapping("/api/v1/admin/categories/{id}")
  public void deleteCategory(@PathVariable long id) {
    catalog.deleteCategory(id);
  }

  @GetMapping("/api/v1/admin/products/{type}")
  public List<Views.ProductView> products(@PathVariable String type) {
    return catalog.products(type).stream().map(ViewFactory::product).toList();
  }

  @PostMapping("/api/v1/admin/products/{type}")
  public Map<String, Object> product(@PathVariable String type, @Valid @RequestBody Product input) {
    return Map.of("id", catalog.save(type, null, input));
  }

  @PutMapping("/api/v1/admin/products/{type}/{id}")
  public void updateProduct(
      @PathVariable String type, @PathVariable long id, @Valid @RequestBody Product input) {
    catalog.save(type, id, input);
  }

  @DeleteMapping("/api/v1/admin/products/{type}/{id}")
  public void deleteProduct(@PathVariable String type, @PathVariable long id) {
    catalog.delete(type, id);
  }

  @GetMapping("/api/v1/admin/employees")
  public List<Views.EmployeeView> employees() {
    return employees.list().stream().map(ViewFactory::employee).toList();
  }

  @GetMapping("/api/v1/admin/couriers")
  public List<Map<String, Object>> couriers() {
    return employees.couriers();
  }

  @PostMapping("/api/v1/admin/employees")
  public Map<String, Object> employee(@Valid @RequestBody Employee input) {
    return Map.of("id", employees.save(null, input));
  }

  @PutMapping("/api/v1/admin/employees/{id}")
  public void updateEmployee(@PathVariable long id, @Valid @RequestBody Employee input) {
    employees.save(id, input);
  }

  @GetMapping("/api/v1/admin/dashboard")
  public Map<String, Object> dashboard() {
    return reports.dashboard();
  }

  @GetMapping("/api/v1/admin/reports")
  public Map<String, Object> reports(@RequestParam LocalDate begin, @RequestParam LocalDate end) {
    return reports.range(begin, end);
  }

  @GetMapping("/api/v1/admin/reports/export")
  public ResponseEntity<byte[]> export(@RequestParam LocalDate begin, @RequestParam LocalDate end) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=campus-report.xlsx")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(reports.export(begin, end));
  }
}

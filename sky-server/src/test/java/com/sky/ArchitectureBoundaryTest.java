package com.sky;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureBoundaryTest {
  @Test
  void controllersCannotOwnDatabaseAccessAndDomainServicesCannotDependOnHttpViews()
      throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    for (var bean : scanner.findCandidateComponents("com.sky.web")) {
      Class<?> type = Class.forName(bean.getBeanClassName());
      for (var field : type.getDeclaredFields())
        assertNotEquals(
            "com.sky.infra.Db",
            field.getType().getName(),
            type.getName() + " must call an application service");
    }
    for (Class<?> service :
        List.of(
            com.sky.platform.CheckoutService.class,
            com.sky.platform.OperationsService.class,
            com.sky.platform.InboxService.class,
            com.sky.platform.CollectionPages.class,
            com.sky.finance.LedgerService.class,
            com.sky.finance.FinanceService.class))
      for (var field : service.getDeclaredFields())
        assertFalse(
            field.getType().getName().startsWith("com.sky.web."),
            service.getName() + " cannot depend on transport views");
  }
}

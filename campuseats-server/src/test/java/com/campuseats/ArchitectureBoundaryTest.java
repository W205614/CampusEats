package com.campuseats;

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
    for (var bean : scanner.findCandidateComponents("com.campuseats.web")) {
      Class<?> type = Class.forName(bean.getBeanClassName());
      for (var field : type.getDeclaredFields())
        assertNotEquals(
            "com.campuseats.infra.Db",
            field.getType().getName(),
            type.getName() + " must call an application service");
    }
    for (Class<?> service :
        List.of(
            com.campuseats.platform.CheckoutService.class,
            com.campuseats.platform.OperationsService.class,
            com.campuseats.platform.InboxService.class,
            com.campuseats.platform.CollectionPages.class,
            com.campuseats.finance.LedgerService.class,
            com.campuseats.finance.FinanceService.class))
      for (var field : service.getDeclaredFields())
        assertFalse(
            field.getType().getName().startsWith("com.campuseats.web."),
            service.getName() + " cannot depend on transport views");
  }
}

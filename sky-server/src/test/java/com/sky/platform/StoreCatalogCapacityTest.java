package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sky.api.Commands.*;
import com.sky.business.MenuCache;
import com.sky.common.BusinessException;
import com.sky.infra.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;

class StoreCatalogCapacityTest {
  private final Db db = mock(Db.class);
  private final MenuCache cache = mock(MenuCache.class);
  private final StoreCatalog catalog = new StoreCatalog(db, mock(PlatformScope.class), cache,
      new Json(new tools.jackson.databind.ObjectMapper()), Clock.systemUTC());

  private Product dish(List<Flavor> flavors) {
    return new Product("边界菜品", 1L, BigDecimal.TEN, true, "", null, flavors, List.of());
  }

  @Test
  void creationLocksSharedStoreBeforeCheckingBothProductTables() {
    when(db.one("SELECT id FROM store WHERE id=? FOR UPDATE", 7L)).thenReturn(Map.of("id", 7L));
    when(db.count(anyString(), eq(7L), eq(7L))).thenReturn(500L);
    var error = assertThrows(BusinessException.class, () -> catalog.save(7, "DISH", null, dish(List.of())));
    assertEquals("CATALOG_LIMIT", error.code());
    var ordered = inOrder(db);
    ordered.verify(db).one("SELECT id FROM store WHERE id=? FOR UPDATE", 7L);
    ordered.verify(db).count("SELECT (SELECT COUNT(*) FROM dish WHERE store_id=?)"
        + "+(SELECT COUNT(*) FROM setmeal WHERE store_id=?)", 7L, 7L);
    verify(db, never()).insert(anyString(), any(Object[].class));
  }

  @Test
  void oversizedHistoricalMenuFailsBeforeCacheOrDetailAllocation() {
    when(db.count(anyString(), eq(7L), eq(7L))).thenReturn(501L);
    var error = assertThrows(BusinessException.class, () -> catalog.products(7, false, null, null));
    assertEquals("CATALOG_LIMIT", error.code());
    verifyNoInteractions(cache);
    verify(db, never()).rows(anyString(), any(Object[].class));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void eachDishHasABoundedNumberOfFlavorGroups() {
    when(db.one("SELECT * FROM category WHERE id=? AND store_id=? FOR SHARE", 1L, 7L))
        .thenReturn(Map.of("id", 1L, "type", 1));
    when(db.one(contains("SELECT p.*"), eq(9L), eq(7L))).thenReturn(Map.of("id", 9L));
    var flavors = java.util.stream.IntStream.range(0, 21)
        .mapToObj(i -> new Flavor("口味" + i, List.of("正常"))).toList();
    var error = assertThrows(BusinessException.class, () -> catalog.save(7, "DISH", 9L, dish(flavors)));
    assertEquals("INVALID_FLAVOR", error.code());
    verify(db, never()).update(anyString(), any(Object[].class));
  }
}

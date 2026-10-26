package com.sky.web;

import com.sky.platform.DiscoveryPages;
import java.lang.reflect.*;
import java.util.*;
import static com.sky.web.PlatformViews.*;

/** Converts existing authorized projections without dropping columns or widening role visibility. */
public final class PlatformViewFactory {
  private PlatformViewFactory() {}

  public static List<OrderResponse> orders(List<Map<String, Object>> rows) {
    return rows.stream().map(PlatformViewFactory::order).toList();
  }

  public static OrderResponse order(Map<String, Object> row) {
    return project(OrderResponse.class, row);
  }

  public static List<RefundResponse> refunds(List<Map<String, Object>> rows) {
    return rows.stream().map(row -> project(RefundResponse.class, row)).toList();
  }

  public static StoreResponse store(Map<String, Object> row) {
    return project(StoreResponse.class, row);
  }

  public static StorePageResponse stores(DiscoveryPages.Page page) {
    return new StorePageResponse(page.records().stream().map(PlatformViewFactory::store).toList(),
        page.nextCursor(), page.hasMore());
  }

  public static SearchPageResponse search(DiscoveryPages.Page page) {
    return new SearchPageResponse(page.records().stream().map(row -> project(ProductResponse.class, row)).toList(),
        page.nextCursor(), page.hasMore());
  }

  private record Shape(RecordComponent[] components, Constructor<?> constructor, Set<String> fields) {}

  private static final ClassValue<Shape> SHAPES = new ClassValue<>() {
    @Override protected Shape computeValue(Class<?> type) {
      if (!type.isRecord() || !Projected.class.isAssignableFrom(type))
        throw new IllegalArgumentException("Expected an explicit platform projection");
      var components = type.getRecordComponents();
      try {
        var constructor = type.getDeclaredConstructor(Arrays.stream(components)
            .map(RecordComponent::getType).toArray(Class<?>[]::new));
        var fields = new HashSet<String>();
        for (var component : components)
          if (!component.getName().equals("presentFields")) fields.add(component.getName());
        return new Shape(components, constructor, Set.copyOf(fields));
      } catch (NoSuchMethodException ex) { throw new IllegalStateException(ex); }
    }
  };

  @SuppressWarnings("unchecked")
  public static <T extends Record & Projected> T project(Class<T> type, Map<String, Object> source) {
    var row = (Map<String, Object>) ApiAdvice.wire(source);
    Shape shape = SHAPES.get(type);
    var unknown = new TreeSet<>(row.keySet());
    unknown.removeAll(shape.fields());
    if (!unknown.isEmpty())
      throw new IllegalStateException("Unmapped " + type.getSimpleName() + " fields: " + unknown);
    Object[] values = new Object[shape.components().length];
    for (int i = 0; i < values.length; i++) {
      var component = shape.components()[i];
      values[i] = component.getName().equals("presentFields")
          ? Set.copyOf(row.keySet()) : convert(row.get(component.getName()), component.getGenericType());
    }
    try { return (T) shape.constructor().newInstance(values); }
    catch (ReflectiveOperationException ex) { throw new IllegalStateException("Invalid " + type.getSimpleName(), ex); }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object convert(Object value, Type type) {
    if (value == null) return null;
    if (type instanceof ParameterizedType parameter && parameter.getRawType() == List.class) {
      if (!(value instanceof List<?> list)) throw new IllegalStateException("Expected projection list");
      return list.stream().map(item -> convert(item, parameter.getActualTypeArguments()[0])).toList();
    }
    if (type instanceof Class<?> cls) {
      if (cls.isRecord() && Projected.class.isAssignableFrom(cls)) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalStateException("Expected nested projection");
        return project((Class) cls, (Map<String, Object>) value);
      }
      if (cls.isInstance(value)) return value;
    }
    throw new IllegalStateException("Projection value does not match " + type.getTypeName());
  }
}

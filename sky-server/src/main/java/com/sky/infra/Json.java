package com.sky.infra;

import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class Json {
  private final ObjectMapper mapper;

  public Json(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String write(Object value) {
    return mapper.writeValueAsString(value);
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> flavors(String text) {
    return mapper.readValue(text, TreeMap.class);
  }

  @SuppressWarnings("unchecked")
  public List<String> strings(String text) {
    return mapper.readValue(text, List.class);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> map(String text) {
    return mapper.readValue(text, LinkedHashMap.class);
  }
}

package com.sky.web;

import com.sky.api.Commands.ApiResponse;
import com.sky.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.core.MethodParameter;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.sky.web")
public class ApiAdvice implements ResponseBodyAdvice<Object> {
  private final HttpServletRequest request;
  private static final Logger log = LoggerFactory.getLogger(ApiAdvice.class);

  public ApiAdvice(HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public boolean supports(MethodParameter p, Class<? extends HttpMessageConverter<?>> c) {
    return !p.getContainingClass().equals(ApiAdvice.class)
        && !byte[].class.equals(p.getParameterType());
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter p,
      MediaType t,
      Class<? extends HttpMessageConverter<?>> c,
      ServerHttpRequest req,
      ServerHttpResponse res) {
    if (body instanceof ApiResponse<?> || body instanceof byte[]) return body;
    return new ApiResponse<>(
        "OK", "", wire(body), String.valueOf(request.getAttribute("requestId")));
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return error(e.status(), e.code(), e.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    jakarta.validation.ConstraintViolationException.class,
    org.springframework.web.bind.ServletRequestBindingException.class,
    org.springframework.web.multipart.MultipartException.class
  })
  ResponseEntity<?> bad(Exception e) {
    return error(400, "INVALID_INPUT", "请求参数不正确");
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<?> duplicate(Exception e) {
    return error(409, "DUPLICATE", "数据冲突，请刷新后重试");
  }

  @ExceptionHandler({
    CannotAcquireLockException.class,
    org.springframework.dao.ConcurrencyFailureException.class
  })
  ResponseEntity<?> concurrent(Exception e) {
    log.warn(
        "Transaction conflict; requestId={} type={}",
        request.getAttribute("requestId"),
        e.getClass().getSimpleName());
    return error(409, "CONFLICT", "数据正在变化，请刷新后重试");
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> upload(Exception e) {
    return error(400, "UPLOAD_TOO_LARGE", "图片不能超过5MB");
  }

  @ExceptionHandler({
    DataAccessException.class,
    org.springframework.transaction.CannotCreateTransactionException.class
  })
  ResponseEntity<?> db(Exception e) {
    log.error("Database request failed; requestId={}", request.getAttribute("requestId"), e);
    return error(503, "DATABASE_UNAVAILABLE", "服务暂不可用");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> other(Exception e) {
    log.error("Request failed; requestId={}", request.getAttribute("requestId"), e);
    return error(500, "INTERNAL_ERROR", "服务处理失败，请稍后重试");
  }

  private ResponseEntity<?> error(int status, String code, String message) {
    return ResponseEntity.status(status)
        .body(
            new ApiResponse<>(
                code, message, null, String.valueOf(request.getAttribute("requestId"))));
  }

  public static Object wire(Object value) {
    if (value == null) return null;
    if (value instanceof Long || value instanceof BigDecimal) return value.toString();
    if (value instanceof Timestamp t)
      return t.toLocalDateTime().atOffset(ZoneOffset.ofHours(8)).toString();
    if (value instanceof LocalDateTime t) return t.atOffset(ZoneOffset.ofHours(8)).toString();
    if (value instanceof java.sql.Date d) return d.toLocalDate().toString();
    if (value instanceof java.time.temporal.TemporalAccessor) return value.toString();
    if (value instanceof Map<?, ?> m) {
      Map<String, Object> out = new LinkedHashMap<>();
      m.forEach((k, v) -> out.put(camel(String.valueOf(k)), wire(v)));
      return out;
    }
    if (value instanceof Collection<?> list) return list.stream().map(ApiAdvice::wire).toList();
    if (value.getClass().isRecord()) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (RecordComponent c : value.getClass().getRecordComponents())
        try {
          out.put(c.getName(), wire(c.getAccessor().invoke(value)));
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      return out;
    }
    return value;
  }

  private static String camel(String text) {
    var b = new StringBuilder();
    boolean upper = false;
    for (char c : text.toCharArray()) {
      if (c == '_') {
        upper = true;
        continue;
      }
      b.append(upper ? Character.toUpperCase(c) : c);
      upper = false;
    }
    return b.toString();
  }
}

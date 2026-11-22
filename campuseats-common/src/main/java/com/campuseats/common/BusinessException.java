package com.campuseats.common;

public final class BusinessException extends RuntimeException {
  private final int status;
  private final String code;
  private final Object data;

  public BusinessException(int status, String code, String message) {
    this(status, code, message, null);
  }

  public BusinessException(int status, String code, String message, Object data) {
    super(message);
    this.status = status;
    this.code = code;
    this.data = data;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Object data() {
    return data;
  }

  public static void require(boolean valid, int status, String code, String message) {
    if (!valid) throw new BusinessException(status, code, message);
  }
}

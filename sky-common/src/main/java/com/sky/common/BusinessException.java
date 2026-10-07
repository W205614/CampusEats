package com.sky.common;

public final class BusinessException extends RuntimeException {
  private final int status;
  private final String code;

  public BusinessException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static void require(boolean valid, int status, String code, String message) {
    if (!valid) throw new BusinessException(status, code, message);
  }
}

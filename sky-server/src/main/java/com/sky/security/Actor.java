package com.sky.security;

import com.sky.common.BusinessException;
import org.springframework.security.core.context.SecurityContextHolder;

public record Actor(long id, String type, String role, String jti, boolean mustChangePassword) {
  public static Actor current() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    BusinessException.require(
        a != null && a.getPrincipal() instanceof Actor, 401, "UNAUTHORIZED", "请先登录");
    return (Actor) a.getPrincipal();
  }

  public static Actor user() {
    Actor a = current();
    BusinessException.require(a.type.equals("USER"), 403, "FORBIDDEN", "仅用户可执行此操作");
    return a;
  }

  public static Actor employee() {
    Actor a = current();
    BusinessException.require(a.type.equals("ADMIN"), 403, "FORBIDDEN", "仅员工可执行此操作");
    return a;
  }

  public static void admin() {
    BusinessException.require(employee().role.equals("ADMIN"), 403, "FORBIDDEN", "需要管理员权限");
  }
}

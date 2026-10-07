package com.sky.utils;

import com.sky.exception.BaseException;

public final class PageValidation {
    private PageValidation() { }
    public static void check(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BaseException("分页参数无效，页码至少为1，每页数量为1至100");
        }
    }
}

package com.sky.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderReadMapper {
  String FILTER =
      "<where><if test='user != null'>user_id=#{user}</if><if test='courier != null'>AND"
          + " courier_id=#{courier}</if><if test='status != null'>AND status=#{status}</if><if"
          + " test='query != null and query != &quot;&quot;'>AND (number LIKE CONCAT(#{query},'%')"
          + " OR phone=#{query})</if></where>";

  @Select("<script>SELECT COUNT(*) FROM orders " + FILTER + "</script>")
  long count(
      @Param("user") Long user,
      @Param("courier") Long courier,
      @Param("status") Integer status,
      @Param("query") String query);

  @Select(
      "<script>SELECT * FROM orders "
          + FILTER
          + " ORDER BY order_time DESC,id DESC LIMIT #{limit} OFFSET #{offset}</script>")
  List<Map<String, Object>> page(
      @Param("user") Long user,
      @Param("courier") Long courier,
      @Param("status") Integer status,
      @Param("query") String query,
      @Param("limit") int limit,
      @Param("offset") int offset);

  @Select(
      "<script>SELECT * FROM order_detail WHERE order_id IN <foreach collection='ids' item='id'"
          + " open='(' separator=',' close=')'>#{id}</foreach> ORDER BY id</script>")
  List<Map<String, Object>> details(@Param("ids") List<Long> ids);
}

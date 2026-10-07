package com.sky.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CatalogReadMapper {
  @Select(
      "SELECT id,name,category_id,price,image,description FROM dish WHERE category_id=#{category}"
          + " AND status=1 ORDER BY id")
  List<Map<String, Object>> dishes(long category);

  @Select(
      "SELECT s.id,s.name,s.category_id,s.price,s.image,s.description FROM setmeal s WHERE"
          + " s.category_id=#{category} AND s.status=1 AND EXISTS(SELECT 1 FROM setmeal_dish sd"
          + " WHERE sd.setmeal_id=s.id) AND NOT EXISTS(SELECT 1 FROM setmeal_dish sd LEFT JOIN dish"
          + " d ON d.id=sd.dish_id LEFT JOIN category c ON c.id=d.category_id WHERE"
          + " sd.setmeal_id=s.id AND (d.id IS NULL OR d.status<>1 OR c.status<>1 OR sd.copies<1))"
          + " ORDER BY s.id")
  List<Map<String, Object>> setmeals(long category);

  @Select(
      "<script>SELECT * FROM dish_flavor WHERE dish_id IN <foreach collection='ids' item='id'"
          + " open='(' separator=',' close=')'>#{id}</foreach> ORDER BY id</script>")
  List<Map<String, Object>> flavors(@Param("ids") List<Long> ids);

  @Select(
      "<script>SELECT sd.*,d.name current_name,d.status FROM setmeal_dish sd JOIN dish d ON"
          + " d.id=sd.dish_id WHERE setmeal_id IN <foreach collection='ids' item='id' open='('"
          + " separator=',' close=')'>#{id}</foreach> ORDER BY sd.id</script>")
  List<Map<String, Object>> components(@Param("ids") List<Long> ids);
}

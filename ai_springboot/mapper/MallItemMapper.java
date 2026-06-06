package com.example.ai_springboot.mapper;

import com.example.ai_springboot.entity.MallItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MallItemMapper {
    // 获取所有有库存的奖品
    @Select("SELECT * FROM mall_item WHERE stock > 0 ORDER BY required_points ASC")
    List<MallItem> selectAvailableItems();

    // 扣减库存
    @Update("UPDATE mall_item SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int decrementStock(Long id);
    // 根据 ID 查询奖品详情
    @Select("SELECT * FROM mall_item WHERE id = #{id}")
    MallItem selectById(Long id);
    @Insert("INSERT INTO mall_item(name, required_points, stock, image_url, create_time) " +
            "VALUES(#{name}, #{requiredPoints}, #{stock}, #{imageUrl}, NOW())")
    int insertItem(MallItem item);

    @Update("UPDATE mall_item SET name=#{name}, required_points=#{requiredPoints}, stock=#{stock}, image_url=#{imageUrl} WHERE id=#{id}")
    int updateItem(MallItem item);

    @Delete("DELETE FROM mall_item WHERE id = #{id}")
    int deleteById(Long id);
}
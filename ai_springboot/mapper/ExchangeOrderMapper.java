package com.example.ai_springboot.mapper;

import com.example.ai_springboot.entity.ExchangeOrder;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ExchangeOrderMapper {
    // 生成兑换订单
    @Insert("INSERT INTO exchange_order(order_no, user_id, item_id, cost_points, shipping_info, status, create_time) " +
            "VALUES(#{orderNo}, #{userId}, #{itemId}, #{costPoints}, #{shippingInfo}, 0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOrder(ExchangeOrder order);

    // 查询用户的兑换记录
    @Select("SELECT * FROM exchange_order WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<ExchangeOrder> selectByUserId(Long userId);
    // 统计商品兑换总数
    @Select("SELECT COUNT(*) FROM exchange_order")
    int countAll();
    @Select("SELECT * FROM exchange_order ORDER BY create_time DESC")
    List<ExchangeOrder> selectAll();

    @Update("UPDATE exchange_order SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}

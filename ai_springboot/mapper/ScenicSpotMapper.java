package com.example.ai_springboot.mapper;

import com.example.ai_springboot.entity.ScenicSpot;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ScenicSpotMapper {

    // 获取所有景点列表（用于小程序首页推荐和后台管理）
    @Select("SELECT * FROM scenic_spot ORDER BY base_heat DESC")
    List<ScenicSpot> selectAll();

    // 根据 AI 识别的标签获取景点详情（打卡成功后查该给多少积分）
    @Select("SELECT * FROM scenic_spot WHERE ai_label = #{aiLabel}")
    ScenicSpot selectByAiLabel(String aiLabel);



    // 增加景点热度（用户打卡成功后调用）
    @Update("UPDATE scenic_spot SET base_heat = base_heat + 1 WHERE id = #{id}")
    int incrementHeat(Long id);
    // 根据 ID 查询景点的真实数据
    @Select("SELECT * FROM scenic_spot WHERE id = #{id}")
    ScenicSpot selectById(Long id);

    @Insert("INSERT INTO scenic_spot(name, ai_label, latitude, longitude, description, reward_points, image_url, create_time) " +
            "VALUES(#{name}, #{aiLabel}, #{latitude}, #{longitude}, #{description}, #{rewardPoints}, #{imageUrl}, NOW())")
    int insertSpot(ScenicSpot spot);

    // 修改景点
// 修改景点（加上 base_heat）
    @Update("UPDATE scenic_spot SET name=#{name}, ai_label=#{aiLabel}, latitude=#{latitude}, longitude=#{longitude}, reward_points=#{rewardPoints}, base_heat=#{baseHeat}, image_url=#{imageUrl} WHERE id=#{id}")
    int updateSpot(ScenicSpot spot);

    @Delete("DELETE FROM scenic_spot WHERE id = #{id}")
    int deleteById(Long id);
}
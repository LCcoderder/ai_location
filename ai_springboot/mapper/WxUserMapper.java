package com.example.ai_springboot.mapper;

import com.example.ai_springboot.entity.WxUser;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WxUserMapper {

    // 根据微信 openid 查找用户（用于登录）
    @Select("SELECT * FROM wx_user WHERE openid = #{openid}")
    WxUser selectByOpenid(String openid);

    // 新用户注册
    @Insert("INSERT INTO wx_user(openid, nickname, avatar_url, points_balance, create_time) " +
            "VALUES(#{openid}, #{nickname}, #{avatarUrl}, 0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(WxUser user);

    // 查询用户信息
    @Select("SELECT * FROM wx_user WHERE id = #{id}")
    WxUser selectById(Long id);

    // 增加积分（打卡成功时调用）
    @Update("UPDATE wx_user SET points_balance = points_balance + #{points} WHERE id = #{id}")
    int addPoints(@Param("id") Long id, @Param("points") Integer points);
    // 统计总用户量
    @Select("SELECT COUNT(*) FROM wx_user")
    int countAll();
    // 扣除积分（商城兑换时调用，且保证积分不能扣成负数）
    @Update("UPDATE wx_user SET points_balance = points_balance - #{points} WHERE id = #{id} AND points_balance >= #{points}")
    int deductPoints(@Param("id") Long id, @Param("points") Integer points);
    // 获取所有用户列表
    @Select("SELECT * FROM wx_user ORDER BY create_time DESC")
    List<WxUser> selectAll();
    @Update("UPDATE wx_user SET points_balance = #{points} WHERE id = #{id}")
    int updatePoints(@Param("id") Long id, @Param("points") Integer points);

    @Delete("DELETE FROM wx_user WHERE id = #{id}")
    int deleteById(Long id);
}
package com.example.ai_springboot.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CheckinGuardMapper {
    @Insert("INSERT IGNORE INTO checkin_guard(user_id, spot_id, checkin_date, status, request_key, create_time, update_time) " +
            "VALUES(#{userId}, #{spotId}, CURDATE(), 0, #{requestKey}, NOW(), NOW())")
    int insertTodayGuard(@Param("userId") Long userId,
                         @Param("spotId") Long spotId,
                         @Param("requestKey") String requestKey);

    @Select("SELECT COUNT(*) FROM checkin_guard WHERE user_id = #{userId} AND spot_id = #{spotId} AND checkin_date = CURDATE()")
    int countTodayGuard(@Param("userId") Long userId, @Param("spotId") Long spotId);

    @Update("UPDATE checkin_guard SET status = #{status}, record_id = #{recordId}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND spot_id = #{spotId} AND checkin_date = CURDATE()")
    int updateTodayGuard(@Param("userId") Long userId,
                         @Param("spotId") Long spotId,
                         @Param("status") Integer status,
                         @Param("recordId") Long recordId);

    @Delete("DELETE FROM checkin_guard WHERE user_id = #{userId} AND spot_id = #{spotId} AND checkin_date = CURDATE()")
    int deleteTodayGuard(@Param("userId") Long userId, @Param("spotId") Long spotId);
}

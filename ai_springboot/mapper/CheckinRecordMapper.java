package com.example.ai_springboot.mapper;

import com.example.ai_springboot.entity.CheckinRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CheckinRecordMapper {

    // 保存打卡/申诉记录
    @Insert("INSERT INTO checkin_record(user_id, spot_id, user_image_url, status, is_added_to_dataset, postcard_url, create_time) " +
            "VALUES(#{userId}, #{spotId}, #{userImageUrl}, #{status}, 0, #{postcardUrl}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRecord(CheckinRecord record);

    // 查询用户的打卡历史足迹
// 查询用户的打卡历史足迹（🚀 修改：联表查出景点名称 s.name as spotName）
    @Select("SELECT c.*, s.name as spotName FROM checkin_record c " +
            "LEFT JOIN scenic_spot s ON c.spot_id = s.id " +
            "WHERE c.user_id = #{userId} ORDER BY c.create_time DESC")
    List<CheckinRecord> selectByUserId(Long userId);

    // 查询所有待处理的申诉记录（管理员后台用）
    @Select("SELECT * FROM checkin_record WHERE status = 2 ORDER BY create_time ASC")
    List<CheckinRecord> selectPendingAppeals();

    // 更新记录状态（如：申诉通过、已加入数据集等）
    @Update("UPDATE checkin_record SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE checkin_record SET status = #{status} WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("status") Integer status,
                              @Param("expectedStatus") Integer expectedStatus);

    // 标记该照片已被加入AI训练集
    @Update("UPDATE checkin_record SET is_added_to_dataset = 1 WHERE id = #{id}")
    int markAsAddedToDataset(Long id);
    // 根据 ID 查询真实的打卡/申诉记录
    @Select("SELECT * FROM checkin_record WHERE id = #{id}")
    CheckinRecord selectById(Long id);
    // 统计打卡总次数
    @Select("SELECT COUNT(*) FROM checkin_record")
    int countAll();

    // 统计待审的申诉单数量 (status = 2 表示申诉中)
    @Select("SELECT COUNT(*) FROM checkin_record WHERE status = 2")
    int countPending();

    @Select("SELECT COUNT(*) FROM checkin_record " +
            "WHERE user_id = #{userId} AND spot_id = #{spotId} " +
            "AND create_time >= CURDATE() AND create_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY)")
    int countTodayByUserAndSpot(@Param("userId") Long userId, @Param("spotId") Long spotId);
    // 获取所有打卡记录总览
    @Select("SELECT * FROM checkin_record ORDER BY create_time DESC")
    List<CheckinRecord> selectAll();
    @Insert("INSERT INTO checkin_record(user_id, spot_id, user_image_url, status, create_time) " +
            "VALUES(#{userId}, #{spotId}, #{userImageUrl}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CheckinRecord record);
}

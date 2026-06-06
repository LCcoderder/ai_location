package com.example.ai_springboot.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.ai_springboot.common.Result;
import com.example.ai_springboot.entity.CheckinRecord;
import com.example.ai_springboot.entity.ScenicSpot;
import com.example.ai_springboot.mapper.CheckinRecordMapper;
import com.example.ai_springboot.mapper.ScenicSpotMapper;
import com.example.ai_springboot.mapper.WxUserMapper;
import com.example.ai_springboot.service.CheckinService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.UUID;

@Service
public class CheckinServiceImpl implements CheckinService {

    @Resource
    private CheckinRecordMapper checkinRecordMapper;
    @Resource
    private ScenicSpotMapper scenicSpotMapper;
    @Resource
    private WxUserMapper wxUserMapper;

    @Value("${ai-service.url}")
    private String aiServiceUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> processCheckin(Long userId, Long spotId, MultipartFile image, Double latitude, Double longitude) {
        try {
            // 真实查询景点数据
            ScenicSpot spot = scenicSpotMapper.selectById(spotId); // 注意：需要在 ScenicSpotMapper 中加一个 @Select("SELECT * FROM scenic_spot WHERE id = #{id}")
            if (spot == null) {
                return Result.error("景点不存在");
            }

            String fileName = UUID.randomUUID().toString() + ".jpg";
            File tempFile = new File(System.getProperty("java.io.tmpdir") + "/" + fileName);
            image.transferTo(tempFile);

            HttpResponse response = HttpRequest.post(aiServiceUrl)
                    .form("target_spot", spot.getAiLabel())
                    .form("latitude", latitude)
                    .form("longitude", longitude)
                    .form("allowable_radius", 1500)
                    .form("image", tempFile)
                    .execute();

            JSONObject jsonResult = JSONUtil.parseObj(response.body());
            Integer aiCode = jsonResult.getInt("code");

            CheckinRecord record = new CheckinRecord();
            record.setUserId(userId);
            record.setSpotId(spot.getId());
            record.setUserImageUrl("/uploads/" + fileName);

            if (aiCode == 200) {
                record.setStatus(1);
                checkinRecordMapper.insertRecord(record);
                wxUserMapper.addPoints(userId, spot.getRewardPoints());
                scenicSpotMapper.incrementHeat(spot.getId());
                return Result.success("打卡成功！已发放积分：" + spot.getRewardPoints());
            } else {
                record.setStatus(2);
                checkinRecordMapper.insertRecord(record);
                return Result.error(403, "校验未通过：" + jsonResult.getStr("msg") + " (已自动转入申诉流程)");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("微服务调用异常或文件处理失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> handleAppeal(Long recordId, Boolean isPass) {
        // 【真实数据闭环】：查出原始打卡记录
        CheckinRecord record = checkinRecordMapper.selectById(recordId); // 注意：需要在 CheckinRecordMapper 加 selectById
        if (record == null || record.getStatus() != 2) {
            return Result.error("申诉记录不存在或已被处理");
        }

        if (isPass) {
            checkinRecordMapper.updateStatus(recordId, 3); // 3=申诉通过
            // 查出该景点应给的积分，并真实发放给用户
            ScenicSpot spot = scenicSpotMapper.selectById(record.getSpotId());
            wxUserMapper.addPoints(record.getUserId(), spot.getRewardPoints());
            scenicSpotMapper.incrementHeat(spot.getId()); // 补算热度
            return Result.success("申诉已通过，积分已补发给用户");
        } else {
            checkinRecordMapper.updateStatus(recordId, 4); // 4=驳回
            return Result.success("申诉已驳回");
        }
    }
}
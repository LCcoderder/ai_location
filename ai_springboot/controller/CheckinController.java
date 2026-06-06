package com.example.ai_springboot.controller;

import com.example.ai_springboot.common.Result;
import com.example.ai_springboot.concurrent.ConcurrencyGuard;
import com.example.ai_springboot.concurrent.LockToken;
import com.example.ai_springboot.dto.AiCheckinCallbackRequest;
import com.example.ai_springboot.dto.CheckinSubmitResponse;
import com.example.ai_springboot.entity.CheckinRecord;
import com.example.ai_springboot.entity.ScenicSpot;
import com.example.ai_springboot.event.DomainEventPublisher;
import com.example.ai_springboot.mapper.CheckinGuardMapper;
import com.example.ai_springboot.mapper.CheckinRecordMapper;
import com.example.ai_springboot.mapper.ScenicSpotMapper;
import com.example.ai_springboot.mapper.WxUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

    @Resource
    private CheckinRecordMapper checkinRecordMapper;

    @Resource
    private CheckinGuardMapper checkinGuardMapper;

    @Resource
    private ScenicSpotMapper scenicSpotMapper;

    @Resource
    private WxUserMapper wxUserMapper;

    @Resource
    private ConcurrencyGuard concurrencyGuard;

    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Value("${ai-service.url}")
    private String pythonApiUrl;

    @Value("${ai-tour.checkin.lock-ttl-seconds:120}")
    private long checkinLockTtlSeconds;

    @Value("${ai-tour.checkin.ai-async-enabled:false}")
    private boolean aiAsyncEnabled;

    @Value("${ai-tour.checkin.callback-token:}")
    private String aiCallbackToken;

    @PostMapping("/doCheckin")
    public Result<?> doCheckin(@RequestParam("image") MultipartFile file,
                               @RequestParam("userId") Long userId,
                               @RequestParam("spotId") Long spotId,
                               @RequestParam("latitude") Double latitude,
                               @RequestParam("longitude") Double longitude) {

        if (file.isEmpty()) {
            return Result.error("打卡照片不能为空！");
        }

        ScenicSpot spot = scenicSpotMapper.selectById(spotId);
        if (spot == null) {
            return Result.error("打卡的景点不存在！");
        }

        String lockKey = "lock:checkin:user:" + userId + ":spot:" + spotId;
        Optional<LockToken> lock = concurrencyGuard.tryLock(lockKey, Duration.ofSeconds(checkinLockTtlSeconds));
        if (!lock.isPresent()) {
            return Result.error(429, "打卡请求正在处理中，请不要重复点击");
        }

        try {
            String requestKey = UUID.randomUUID().toString();
            if (!reserveTodayCheckin(userId, spotId, requestKey)) {
                return Result.error(409, "该景点今日已提交过打卡，请勿重复打卡");
            }

            String ext = ".jpg";
            if (file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")) {
                ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
            }
            String newFileName = UUID.randomUUID().toString() + ext;
            String uploadDirPath = System.getProperty("user.dir") + "/uploads/";
            File uploadDir = new File(uploadDirPath);
            if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                rollbackTodayGuard(userId, spotId);
                return Result.error("照片目录创建失败！");
            }

            File savedFile = new File(uploadDir, newFileName);
            try {
                file.transferTo(savedFile);
            } catch (IOException e) {
                e.printStackTrace();
                rollbackTodayGuard(userId, spotId);
                return Result.error("照片保存失败！");
            }

            String finalImageUrl = "/uploads/" + newFileName;
            CheckinRecord record = new CheckinRecord();
            record.setUserId(userId);
            record.setSpotId(spotId);
            record.setUserImageUrl(finalImageUrl);

            if (aiAsyncEnabled) {
                record.setStatus(0);
                checkinRecordMapper.insert(record);
                updateTodayGuard(userId, spotId, record.getStatus(), record.getId());
                boolean queued = domainEventPublisher.publishAiCheckinRequest(record, spot, latitude, longitude);
                if (!queued) {
                    checkinRecordMapper.updateStatusIfCurrent(record.getId(), 2, 0);
                    record.setStatus(2);
                    updateTodayGuard(userId, spotId, record.getStatus(), record.getId());
                    domainEventPublisher.publishCheckinEvent(record, spot, "AI 队列暂不可用，已转入人工审核");
                    return Result.error(503, "AI 识别队列暂不可用，已为您转入人工审核");
                }
                domainEventPublisher.publishCheckinEvent(record, spot, "AI 识别任务已提交，正在排队处理");
                return Result.success(new CheckinSubmitResponse(record.getId(), 0, "AI 识别任务已提交，正在排队处理"));
            }

            RestTemplate restTemplate = new RestTemplate();
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

                body.add("target_spot", spot.getAiLabel());
                body.add("latitude", latitude.toString());
                body.add("longitude", longitude.toString());

                byte[] fileBytes = Files.readAllBytes(savedFile.toPath());
                ByteArrayResource fileAsResource = new ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() {
                        return newFileName;
                    }
                };
                body.add("image", fileAsResource);

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                ResponseEntity<Map> response = restTemplate.postForEntity(pythonApiUrl, requestEntity, Map.class);
                Map<String, Object> resBody = response.getBody();

                int aiCode = readCode(resBody);
                String message = readMessage(resBody, "校验失败");
                if (aiCode == 200) {
                    record.setStatus(1);
                    checkinRecordMapper.insert(record);
                    wxUserMapper.addPoints(userId, spot.getRewardPoints());
                    scenicSpotMapper.incrementHeat(spotId);
                    updateTodayGuard(userId, spotId, record.getStatus(), record.getId());
                    domainEventPublisher.publishCheckinEvent(record, spot, message);
                    return Result.success("打卡成功！获得积分: +" + spot.getRewardPoints());
                }

                record.setStatus(readStatus(resBody, 2));
                checkinRecordMapper.insert(record);
                updateTodayGuard(userId, spotId, record.getStatus(), record.getId());
                domainEventPublisher.publishCheckinEvent(record, spot, message);
                return Result.error(message);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("请求 Python AI 接口失败，请检查 app.py 是否运行！");
                record.setStatus(2);
                checkinRecordMapper.insert(record);
                updateTodayGuard(userId, spotId, record.getStatus(), record.getId());
                domainEventPublisher.publishCheckinEvent(record, spot, "AI 服务暂不可用，已转入人工审核");
                return Result.error("AI 服务暂不可用，已为您转入人工审核！");
            }
        } finally {
            concurrencyGuard.unlock(lock.get());
        }
    }

    @GetMapping("/status")
    public Result<CheckinRecord> getCheckinStatus(@RequestParam("recordId") Long recordId) {
        CheckinRecord record = checkinRecordMapper.selectById(recordId);
        if (record == null) {
            return (Result<CheckinRecord>) Result.error("记录不存在");
        }
        return Result.success(record);
    }

    @PostMapping("/ai/callback")
    public Result<?> handleAiCallback(@RequestBody AiCheckinCallbackRequest callback,
                                      @RequestHeader(value = "X-AI-CALLBACK-TOKEN", required = false) String callbackToken) {
        if (StringUtils.hasText(aiCallbackToken) && !aiCallbackToken.equals(callbackToken)) {
            return Result.error(401, "AI 回调令牌无效");
        }
        if (callback.getRecordId() == null) {
            return Result.error("缺少 recordId");
        }

        String lockKey = "lock:checkin:ai-callback:" + callback.getRecordId();
        Optional<LockToken> lock = concurrencyGuard.tryLock(lockKey, Duration.ofSeconds(30));
        if (!lock.isPresent()) {
            return Result.error(429, "该 AI 结果正在处理中，请勿重复回调");
        }

        try {
            CheckinRecord record = checkinRecordMapper.selectById(callback.getRecordId());
            if (record == null) {
                return Result.error("记录不存在");
            }
            if (record.getStatus() == null || record.getStatus() != 0) {
                return Result.error(409, "该打卡记录已完成处理");
            }

            ScenicSpot spot = scenicSpotMapper.selectById(record.getSpotId());
            Integer finalStatus = resolveCallbackStatus(callback);
            int updated = checkinRecordMapper.updateStatusIfCurrent(record.getId(), finalStatus, 0);
            if (updated == 0) {
                return Result.error(409, "该打卡记录已被其他回调处理");
            }

            record.setStatus(finalStatus);
            updateTodayGuard(record.getUserId(), record.getSpotId(), finalStatus, record.getId());

            if (finalStatus == 1 && spot != null) {
                wxUserMapper.addPoints(record.getUserId(), spot.getRewardPoints());
                scenicSpotMapper.incrementHeat(spot.getId());
            }

            domainEventPublisher.publishCheckinEvent(record, spot, callback.getMsg());
            return Result.success("AI 识别结果已写回");
        } finally {
            concurrencyGuard.unlock(lock.get());
        }
    }

    @PostMapping("/admin/handleAppeal")
    public Result<?> handleAppeal(@RequestParam("recordId") Long recordId,
                                  @RequestParam("isPass") Boolean isPass) {
        String lockKey = "lock:checkin:appeal:" + recordId;
        Optional<LockToken> lock = concurrencyGuard.tryLock(lockKey, Duration.ofSeconds(30));
        if (!lock.isPresent()) {
            return Result.error(429, "该申诉正在处理中，请不要重复操作");
        }

        try {
            CheckinRecord record = checkinRecordMapper.selectById(recordId);
            if (record == null) {
                return Result.error("记录不存在");
            }
            if (record.getStatus() == null || record.getStatus() != 2) {
                return Result.error(409, "该申诉已处理，请勿重复审批");
            }

            int targetStatus = Boolean.TRUE.equals(isPass) ? 3 : 4;
            int updated = checkinRecordMapper.updateStatusIfCurrent(recordId, targetStatus, 2);
            if (updated == 0) {
                return Result.error(409, "该申诉已被其他请求处理");
            }

            record.setStatus(targetStatus);
            ScenicSpot spot = scenicSpotMapper.selectById(record.getSpotId());
            if (Boolean.TRUE.equals(isPass)) {
                if (spot != null) {
                    wxUserMapper.addPoints(record.getUserId(), spot.getRewardPoints());
                    scenicSpotMapper.incrementHeat(spot.getId());
                }
                domainEventPublisher.publishCheckinEvent(record, spot, "申诉通过，积分已补发");
                return Result.success("申诉已通过，积分已为您补发！");
            }

            domainEventPublisher.publishCheckinEvent(record, spot, "申诉驳回");
            return Result.success("申诉已驳回！");
        } finally {
            concurrencyGuard.unlock(lock.get());
        }
    }

    private boolean reserveTodayCheckin(Long userId, Long spotId, String requestKey) {
        try {
            return checkinGuardMapper.insertTodayGuard(userId, spotId, requestKey) > 0;
        } catch (DataAccessException e) {
            return checkinRecordMapper.countTodayByUserAndSpot(userId, spotId) == 0;
        }
    }

    private void updateTodayGuard(Long userId, Long spotId, Integer status, Long recordId) {
        try {
            checkinGuardMapper.updateTodayGuard(userId, spotId, status, recordId);
        } catch (DataAccessException ignored) {
            // The guard table is added by the concurrency SQL patch. Old databases can still run with lock fallback.
        }
    }

    private void rollbackTodayGuard(Long userId, Long spotId) {
        try {
            checkinGuardMapper.deleteTodayGuard(userId, spotId);
        } catch (DataAccessException ignored) {
            // No guard table, no rollback required.
        }
    }

    private int readCode(Map<String, Object> resBody) {
        if (resBody == null || resBody.get("code") == null) {
            return 500;
        }
        Object code = resBody.get("code");
        if (code instanceof Number) {
            return ((Number) code).intValue();
        }
        return Integer.parseInt(String.valueOf(code));
    }

    @SuppressWarnings("unchecked")
    private Integer readStatus(Map<String, Object> resBody, Integer defaultStatus) {
        if (resBody == null || resBody.get("data") == null) {
            return defaultStatus;
        }
        Object data = resBody.get("data");
        if (!(data instanceof Map)) {
            return defaultStatus;
        }
        Object status = ((Map<String, Object>) data).get("status");
        if (status instanceof Number) {
            return ((Number) status).intValue();
        }
        return status == null ? defaultStatus : Integer.parseInt(String.valueOf(status));
    }

    private String readMessage(Map<String, Object> resBody, String defaultMessage) {
        if (resBody == null || resBody.get("msg") == null) {
            return defaultMessage;
        }
        return String.valueOf(resBody.get("msg"));
    }

    private Integer resolveCallbackStatus(AiCheckinCallbackRequest callback) {
        if (callback.getCode() != null && callback.getCode() == 200) {
            return 1;
        }
        if (callback.getStatus() != null) {
            return callback.getStatus();
        }
        return 2;
    }
}

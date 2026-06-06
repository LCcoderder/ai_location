

package com.example.ai_springboot.controller;

import com.example.ai_springboot.common.Result;
import com.example.ai_springboot.entity.*;
import com.example.ai_springboot.service.WxUserService;
import com.example.ai_springboot.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper; // 🚀 引入了 JSON 解析工具
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

    @RestController
    @RequestMapping("/api/user")
    public class WxUserController {

        // ⚠️ TODO: 填入你的微信小程序真实 AppID 和 AppSecret
        private static final String APP_ID = "wx9e3c3cf980b17377";
        private static final String APP_SECRET = "dc3cc7be2e0ee79b45e4cd7c835e4389";

        @Resource private WxUserService wxUserService;
        @Resource private CheckinRecordMapper checkinRecordMapper;
        @Resource private ExchangeOrderMapper exchangeOrderMapper;
        @Resource private CommunityPostMapper communityPostMapper;

        // 保留老的模拟登录以防万一
        @PostMapping("/login")
        public Result<WxUser> login(String openid, String nickname, String avatarUrl) {
            return Result.success(wxUserService.loginOrRegister(openid, nickname, avatarUrl));
        }

        /**
         * 🚀 终极稳定版：处理微信真实一键登录的接口
         */
        @PostMapping("/wxRealLogin")
        public Result<WxUser> wxRealLogin(String code, String nickname, String avatarUrl) {
            if (code == null || code.isEmpty()) {
                return (Result<WxUser>) Result.error("缺少微信登录凭证 code");
            }

            // 1. 拼装请求微信官方服务器的 URL 获取 OpenID
            String url = String.format("https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    APP_ID, APP_SECRET, code);

            RestTemplate restTemplate = new RestTemplate();
            try {
                // 2. 🚀 核心修复：用 String 接收微信的返回值，无视它的 text/plain 格式
                String jsonResponse = restTemplate.getForObject(url, String.class);
                System.out.println("微信官方原始返回：" + jsonResponse); // 打印在控制台，方便你查看微信是不是报错了

                if (jsonResponse != null && !jsonResponse.isEmpty()) {
                    // 3. 手动把 JSON 字符串转成 Map 对象
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> responseMap = mapper.readValue(jsonResponse, Map.class);

                    // 4. 判断是否成功拿到了 openid
                    if (responseMap.containsKey("openid")) {
                        String openid = (String) responseMap.get("openid");
                        // 调用 Service 完成数据库的注册或信息更新
                        WxUser user = wxUserService.loginOrRegister(openid, nickname, avatarUrl);
                        return Result.success(user);
                    } else {
                        // 如果没有 openid，通常是因为 code 过期或者 appid/secret 填错了
                        String errMsg = (String) responseMap.get("errmsg");
                        System.out.println("微信官方验证返回错误：" + responseMap);
                        return (Result<WxUser>) Result.error("登录失败：" + errMsg);
                    }
                }
                return (Result<WxUser>) Result.error("微信接口返回为空");

            } catch (Exception e) {
                e.printStackTrace();
                return (Result<WxUser>) Result.error("服务器请求微信接口异常：" + e.getMessage());
            }
        }

        @GetMapping("/info")
        public Result<WxUser> getUserInfo(Long userId) {
            return Result.success(wxUserService.getUserInfo(userId));
        }

        @GetMapping("/checkins")
        public Result<List<CheckinRecord>> getMyCheckins(Long userId) {
            return Result.success(checkinRecordMapper.selectByUserId(userId));
        }

        @GetMapping("/orders")
        public Result<List<ExchangeOrder>> getMyOrders(Long userId) {
            return Result.success(exchangeOrderMapper.selectByUserId(userId));
        }

        @GetMapping("/posts")
        public Result<List<CommunityPost>> getMyPosts(Long userId) {
            return Result.success(communityPostMapper.selectByUserId(userId));
        }
    }
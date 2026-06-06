-- 创建数据库（支持 Emoji 表情）
CREATE DATABASE IF NOT EXISTS `ai_tour_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ai_tour_db`;

-- ----------------------------
-- 1. 用户与权限模块
-- ----------------------------

-- 微信用户表
DROP TABLE IF EXISTS `wx_user`;
CREATE TABLE `wx_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键，用户ID',
  `openid` varchar(50) NOT NULL COMMENT '微信用户唯一标识',
  `nickname` varchar(50) DEFAULT NULL COMMENT '用户昵称',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '用户头像链接',
  `points_balance` int(11) NOT NULL DEFAULT '0' COMMENT '当前积分余额（闭环核心：打卡增加，兑换扣除）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信用户表';

-- 系统管理员表
DROP TABLE IF EXISTS `sys_admin`;
CREATE TABLE `sys_admin` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) NOT NULL COMMENT '后台登录账号',
  `password` varchar(100) NOT NULL COMMENT '加密后的密码',
  `role` varchar(20) DEFAULT 'ADMIN' COMMENT '角色权限',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统管理员表';


-- ----------------------------
-- 2. 文旅基础数据模块
-- ----------------------------

-- 文旅景点表
DROP TABLE IF EXISTS `scenic_spot`;
CREATE TABLE `scenic_spot` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键，景点ID',
  `name` varchar(100) NOT NULL COMMENT '景点名称',
  `ai_label` varchar(50) NOT NULL COMMENT 'AI模型对应的分类标签（对应Python里的class_names）',
  `latitude` decimal(10,6) NOT NULL COMMENT '纬度（用于LBS校验）',
  `longitude` decimal(10,6) NOT NULL COMMENT '经度（用于LBS校验）',
  `description` text COMMENT '景点历史故事或简介',
  `reward_points` int(11) NOT NULL DEFAULT '10' COMMENT '成功打卡该景点的奖励积分',
  `base_heat` int(11) NOT NULL DEFAULT '0' COMMENT '基础热度值（配合真实打卡用于测算人流）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_label` (`ai_label`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文旅景点表';


-- ----------------------------
-- 3. 核心业务模块（AI 打卡与申诉）
-- ----------------------------

-- 打卡与申诉记录表
DROP TABLE IF EXISTS `checkin_record`;
CREATE TABLE `checkin_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '打卡用户ID',
  `spot_id` bigint(20) NOT NULL COMMENT '景点ID',
  `user_image_url` varchar(255) NOT NULL COMMENT '用户现场拍摄上传的图片',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：0=AI识别中，1=AI识别成功，2=识别失败用户申诉中，3=申诉通过(发积分)，4=申诉驳回',
  `is_added_to_dataset` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已加入AI训练素材库(0=否, 1=是)',
  `postcard_url` varchar(255) DEFAULT NULL COMMENT '生成的专属电子明信片地址',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '打卡/申诉时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_spot_id` (`spot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡与申诉记录表（AI错题本核心逻辑）';


-- ----------------------------
-- 4. 商业与促活模块
-- ----------------------------

-- 积分商城奖品表
DROP TABLE IF EXISTS `mall_item`;
CREATE TABLE `mall_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键，商品ID',
  `name` varchar(100) NOT NULL COMMENT '奖品名称（如：大雁塔冰箱贴）',
  `required_points` int(11) NOT NULL COMMENT '兑换所需积分',
  `stock` int(11) NOT NULL DEFAULT '0' COMMENT '剩余库存',
  `image_url` varchar(255) DEFAULT NULL COMMENT '奖品展示图',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商城奖品表';

-- 积分兑换订单表
DROP TABLE IF EXISTS `exchange_order`;
CREATE TABLE `exchange_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(50) NOT NULL COMMENT '订单流水号',
  `user_id` bigint(20) NOT NULL COMMENT '兑换者ID',
  `item_id` bigint(20) NOT NULL COMMENT '兑换的商品ID',
  `cost_points` int(11) NOT NULL COMMENT '实际扣除的积分',
  `shipping_info` varchar(255) DEFAULT NULL COMMENT '邮寄地址或线下核销信息',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态：0=待发货/待核销，1=已完成',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '兑换时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换订单表';


-- ----------------------------
-- 5. 社区内容模块
-- ----------------------------

-- 漫游社区帖子表
DROP TABLE IF EXISTS `community_post`;
CREATE TABLE `community_post` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键，帖子ID',
  `user_id` bigint(20) NOT NULL COMMENT '发帖人ID',
  `spot_id` bigint(20) DEFAULT NULL COMMENT '关联的景点ID（可选）',
  `content` text NOT NULL COMMENT '图文分享内容',
  `images_json` json DEFAULT NULL COMMENT '帖子附带的图片集合（JSON数组格式）',
  `is_featured` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否精选推送到首页 (0=普通, 1=精选)',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：0=待审，1=正常，2=违规下架',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发帖时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漫游社区帖子表';

-- 帖子评论表
DROP TABLE IF EXISTS `post_comment`;
CREATE TABLE `post_comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键，评论ID',
  `post_id` bigint(20) NOT NULL COMMENT '关联的帖子ID',
  `user_id` bigint(20) NOT NULL COMMENT '评论人ID',
  `content` varchar(500) NOT NULL COMMENT '评论内容',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子评论表';

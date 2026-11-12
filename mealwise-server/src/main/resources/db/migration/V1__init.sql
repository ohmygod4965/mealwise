-- Flyway V1：完整基线（表结构 + 46 条食物 + 3 个演示账号 + 3 个演示餐次）
-- 由 docs/init.sql 生成；全新数据库由应用启动时自动执行。
-- 已有数据库请依赖 baseline-on-migrate（baseline-version=1），不会重复建表。

-- 连接字符集固定 utf8mb4，避免中文种子变成乱码
SET NAMES utf8mb4;

-- 有外键依赖，先删子表
DROP TABLE IF EXISTS `ai_audit_log`;
DROP TABLE IF EXISTS `meal_item`;
DROP TABLE IF EXISTS `meal`;
DROP TABLE IF EXISTS `food`;
DROP TABLE IF EXISTS `sys_user`;

-- ---------------------------------------------------------------------
-- 用户
-- ---------------------------------------------------------------------
CREATE TABLE `sys_user` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)     NOT NULL                COMMENT '登录名，全局唯一',
    `password_hash` CHAR(60)        NOT NULL                COMMENT 'BCrypt 哈希（定长 60）',
    `allergens`     VARCHAR(255)    DEFAULT NULL            COMMENT '过敏原 token，逗号分隔，如 peanut,seafood',
    `role`          VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN / USER',
    `status`        TINYINT         NOT NULL DEFAULT 1      COMMENT '1=启用 0=禁用（禁用后登录被拒、token 失效）',
    `gender`        VARCHAR(8)      DEFAULT NULL            COMMENT 'MALE / FEMALE',
    `height_cm`     DECIMAL(5,1)    DEFAULT NULL            COMMENT '身高 cm',
    `weight_kg`     DECIMAL(5,1)    DEFAULT NULL            COMMENT '体重 kg',
    `age`           INT             DEFAULT NULL            COMMENT '年龄（可选，缺省按 30 计算）',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户';

-- ---------------------------------------------------------------------
-- 食物库（全局共享）
-- ---------------------------------------------------------------------
CREATE TABLE `food` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`               VARCHAR(64)     NOT NULL                COMMENT '食物名称',
    `category`           VARCHAR(32)     NOT NULL                COMMENT '类别：主食/肉蛋/蔬菜/水果/乳制品/坚果/豆制品/饮料',
    `calories_per_100g`  DECIMAL(6,1)    NOT NULL                COMMENT '每 100g 热量 kcal',
    `protein_per_100g`   DECIMAL(5,1)    NOT NULL                COMMENT '每 100g 蛋白质 g',
    `fat_per_100g`       DECIMAL(5,1)    NOT NULL                COMMENT '每 100g 脂肪 g',
    `carb_per_100g`      DECIMAL(5,1)    NOT NULL                COMMENT '每 100g 碳水 g',
    `unit_name`          VARCHAR(16)     DEFAULT NULL            COMMENT '计量单位名，如 碗/个/片',
    `unit_weight_g`      DECIMAL(7,1)    DEFAULT NULL            COMMENT '1 单位约多少克',
    `allergens`          VARCHAR(255)    DEFAULT NULL            COMMENT '过敏原 token，逗号分隔',
    `enabled`            TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '1 上架 / 0 软删除',
    `created_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_food_cat_enabled_cal` (`category`, `enabled`, `calories_per_100g`),
    KEY `idx_food_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '食物库';

-- ---------------------------------------------------------------------
-- 餐次（user_id 是数据隔离边界）
-- ---------------------------------------------------------------------
CREATE TABLE `meal` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属用户，隔离边界',
    `meal_type`  VARCHAR(16)     NOT NULL COMMENT 'BREAKFAST/LUNCH/DINNER/SNACK',
    `eaten_at`   DATETIME(3)     NOT NULL COMMENT '用餐时间',
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_meal_user_time` (`user_id`, `eaten_at`),
    CONSTRAINT `fk_meal_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '餐次';

-- ---------------------------------------------------------------------
-- 餐次食物项（只存 food_id + 克数，不冗余热量）
-- ---------------------------------------------------------------------
CREATE TABLE `meal_item` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `meal_id`    BIGINT UNSIGNED NOT NULL,
    `food_id`    BIGINT UNSIGNED NOT NULL,
    `quantity_g` DECIMAL(7,1)    NOT NULL COMMENT '克数，> 0',
    `version`    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（并发改克数时防丢更新）',
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_item_meal` (`meal_id`),
    KEY `idx_item_food` (`food_id`),
    CONSTRAINT `fk_item_meal` FOREIGN KEY (`meal_id`) REFERENCES `meal` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_item_food` FOREIGN KEY (`food_id`) REFERENCES `food` (`id`) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '餐次食物项';

-- ---------------------------------------------------------------------
-- AI 调用审计（谁、哪个能力、模型还是降级、耗时；不存用户原文）
-- ---------------------------------------------------------------------
CREATE TABLE `ai_audit_log` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `feature`    VARCHAR(32)     NOT NULL COMMENT 'parse-meal/explain/ask/agent/meal-plan',
    `source`     VARCHAR(16)     DEFAULT NULL COMMENT 'llm/rule/retrieval/disabled',
    `result`     VARCHAR(16)     NOT NULL COMMENT 'success/failure',
    `latency_ms` BIGINT          DEFAULT NULL,
    `detail`     VARCHAR(255)    DEFAULT NULL COMMENT '失败原因，不含用户原文',
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_ai_audit_user_time` (`user_id`, `created_at`),
    CONSTRAINT `fk_ai_audit_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 调用审计';

-- =====================================================================
-- 种子数据：46 条食物，覆盖验收用例（米饭 / 鸡胸肉 / 西兰花）
-- 每个类别都刻意安排了「热量高于基准」的候选，保证 higher 策略有结果。
-- =====================================================================
INSERT INTO `food`
(`name`, `category`, `calories_per_100g`, `protein_per_100g`, `fat_per_100g`, `carb_per_100g`, `unit_name`, `unit_weight_g`, `allergens`, `enabled`)
VALUES
-- 主食（基准：米饭 116）
('米饭',          '主食', 116.0,  2.6, 0.3, 25.9, '碗', 200.0, NULL,            1),
('蛋炒饭',        '主食', 180.0,  5.0, 5.5, 29.0, '碗', 250.0, 'egg',           1),
('馒头',          '主食', 223.0,  7.0, 1.1, 47.0, '个', 100.0, 'gluten',        1),
('花卷',          '主食', 217.0,  6.4, 1.0, 45.6, '个', 90.0,  'gluten',        1),
('猪肉白菜饺子',  '主食', 240.0,  9.0, 8.0, 33.0, '个', 20.0,  'gluten',        1),
('油条',          '主食', 388.0,  6.9, 17.6, 51.0,'根', 60.0,  'gluten',        1),
('全麦面包',      '主食', 246.0,  9.0, 3.3, 45.0, '片', 30.0,  'gluten',        1),
('小米粥',        '主食', 46.0,   1.4, 0.7, 8.4,  '碗', 300.0, NULL,            1),
('蒸红薯',        '主食', 86.0,   1.6, 0.2, 20.1, '个', 150.0, NULL,            1),
('煮玉米',        '主食', 112.0,  4.0, 1.2, 22.8, '根', 200.0, NULL,            1),
('意大利面(煮)',  '主食', 158.0,  5.8, 0.9, 30.9, '份', 200.0, 'gluten',        1),

-- 肉蛋（基准：鸡胸肉 133）
('鸡胸肉',        '肉蛋', 133.0,  24.6, 2.5, 0.6,  '份', 150.0, NULL,            1),
('鸡腿肉',        '肉蛋', 181.0,  20.0, 10.9, 0.0, '份', 150.0, NULL,            1),
('猪里脊',        '肉蛋', 155.0,  20.2, 7.9, 0.7,  '份', 150.0, NULL,            1),
('五花肉',        '肉蛋', 518.0,  9.3, 53.0, 0.0,  '份', 100.0, NULL,            1),
('牛里脊',        '肉蛋', 106.0,  22.2, 1.9, 2.4,  '份', 150.0, NULL,            1),
('鸡蛋',          '肉蛋', 144.0,  13.3, 8.8, 2.8,  '个', 50.0,  'egg',           1),
('鸭肉',          '肉蛋', 240.0,  15.5, 19.7, 0.2, '份', 150.0, NULL,            1),
('三文鱼',        '肉蛋', 139.0,  20.4, 5.9, 0.0,  '份', 120.0, 'seafood',       1),
('基围虾',        '肉蛋', 93.0,   18.6, 0.8, 2.8,  '份', 120.0, 'seafood',       1),

-- 蔬菜（基准：西兰花 34）
('西兰花',        '蔬菜', 34.0,   4.1, 0.6, 4.3,   '份', 100.0, NULL,            1),
('土豆',          '蔬菜', 77.0,   2.0, 0.2, 17.2,  '个', 150.0, NULL,            1),
('胡萝卜',        '蔬菜', 39.0,   1.0, 0.2, 8.8,   '根', 120.0, NULL,            1),
('菠菜',          '蔬菜', 24.0,   2.6, 0.3, 4.5,   '份', 100.0, NULL,            1),
('生菜',          '蔬菜', 15.0,   1.3, 0.3, 2.0,   '份', 100.0, NULL,            1),
('番茄',          '蔬菜', 20.0,   0.9, 0.2, 4.0,   '个', 150.0, NULL,            1),
('黄瓜',          '蔬菜', 16.0,   0.8, 0.2, 2.9,   '根', 200.0, NULL,            1),
('南瓜',          '蔬菜', 26.0,   0.7, 0.1, 6.5,   '份', 150.0, NULL,            1),
('毛豆',          '蔬菜', 131.0,  13.1, 5.0, 10.5, '份', 100.0, 'soy',           1),
('香菇',          '蔬菜', 26.0,   2.2, 0.3, 5.2,   '份', 100.0, NULL,            1),

-- 水果
('苹果',          '水果', 52.0,   0.3, 0.2, 13.8,  '个', 200.0, NULL,            1),
('香蕉',          '水果', 89.0,   1.1, 0.3, 22.8,  '根', 120.0, NULL,            1),
('橙子',          '水果', 47.0,   0.9, 0.1, 11.8,  '个', 180.0, NULL,            1),
('牛油果',        '水果', 160.0,  2.0, 14.7, 8.5,  '个', 150.0, NULL,            1),
('西瓜',          '水果', 25.0,   0.6, 0.1, 6.8,   '份', 300.0, NULL,            1),

-- 乳制品
('纯牛奶',        '乳制品', 54.0,  3.0, 3.2, 3.4,   '杯', 250.0, 'dairy',         1),
('原味酸奶',      '乳制品', 72.0,  2.5, 2.7, 9.3,   '杯', 150.0, 'dairy',         1),
('切达奶酪',      '乳制品', 328.0, 25.0, 25.0, 3.0, '片', 20.0,  'dairy',         1),

-- 坚果
('花生',          '坚果', 567.0,  25.8, 49.2, 16.1, '把', 30.0,  'peanut',        1),
('核桃',          '坚果', 646.0,  15.2, 65.2, 13.7, '把', 30.0,  'nut',           1),
('巴旦木',        '坚果', 578.0,  21.2, 50.6, 21.7, '把', 30.0,  'nut',           1),

-- 豆制品 / 饮料
('北豆腐',        '豆制品', 81.0,  8.1, 4.2, 4.2,   '份', 150.0, 'soy',           1),
('豆干',          '豆制品', 140.0, 16.2, 7.2, 4.2,  '份', 100.0, 'soy',           1),
('无糖豆浆',      '饮料', 31.0,   3.0, 1.6, 1.2,    '杯', 250.0, 'soy',           1),
('可乐',          '饮料', 43.0,   0.0, 0.0, 10.6,   '罐', 330.0, NULL,            1),
('珍珠奶茶',      '饮料', 89.0,   1.0, 2.5, 16.0,   '杯', 500.0, 'dairy',         1);

-- =====================================================================
-- 演示账号（密码见 README §3.3；BCrypt cost=10）
-- 仅用于验收「JWT 登录 / 越权 403 / 过敏原过滤」，生产环境请删除这三行
-- =====================================================================
INSERT INTO `sys_user`
(`id`, `username`, `password_hash`, `allergens`, `role`, `status`, `gender`, `height_cm`, `weight_kg`, `age`)
VALUES
(1, 'demo',    '$2a$10$fMFjyjqdh1ds9KZVS6hhfeTDtbQCFpIAtQg0zvAV7maoPvVc97ejm', NULL,  'ADMIN', 1, 'MALE',   178.0, 70.0, 28),
(2, 'other',   '$2a$10$s3xYspM8P05bPJtP/C3Yr.ai8Csux4cYsUrdo.OCH9YSTqTfRp4Tu', NULL,  'USER',  1, 'FEMALE', 165.0, 55.0, 26),
(3, 'allergy', '$2a$10$ds3.OBZqB2WZFIiC4A2.FeCvfMY57cOLKTwOV47E/bXQzAqh4vl5W', 'egg', 'USER',  1, NULL,     NULL,  NULL, NULL);

-- =====================================================================
-- 演示餐次：meal 1 / 2 属于 demo（1 号是验收用例：米饭 + 鸡胸肉 + 西兰花），
-- meal 3 属于 other。用 other 的 token 访问 /api/meals/1/calories 必须返回 403。
-- food_id 用子查询按名字取，不依赖自增 ID 的顺序。
-- =====================================================================
INSERT INTO `meal` (`id`, `user_id`, `meal_type`, `eaten_at`) VALUES
(1, 1, 'LUNCH',  '2026-01-01 12:30:00'),
(2, 1, 'DINNER', '2026-01-01 19:00:00'),
(3, 2, 'LUNCH',  '2026-01-01 12:00:00');

INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 1, `id`, 200.0 FROM `food` WHERE `name` = '米饭';
INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 1, `id`, 150.0 FROM `food` WHERE `name` = '鸡胸肉';
INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 1, `id`, 100.0 FROM `food` WHERE `name` = '西兰花';
INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 2, `id`, 150.0 FROM `food` WHERE `name` = '牛里脊';
INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 3, `id`, 200.0 FROM `food` WHERE `name` = '猪肉白菜饺子';

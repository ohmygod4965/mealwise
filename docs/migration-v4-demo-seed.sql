-- =====================================================================
-- Mealwise 迁移 v4：演示账号 + 演示餐次（JWT 登录 / 越权 403 验收用）
--
-- 仅需在**已有库**上执行一次；全新安装直接用 init.sql（已包含这些数据）。
-- 幂等：用户用 INSERT IGNORE（按主键/唯一键跳过），餐次项用 NOT EXISTS 去重。
--
-- 执行：mysql -u root -p < docs/migration-v4-demo-seed.sql
-- =====================================================================

USE `mealwise`;

-- 1) 演示账号（BCrypt cost=10，明文密码见 README §3.3）
INSERT IGNORE INTO `sys_user`
(`id`, `username`, `password_hash`, `allergens`, `role`, `status`, `gender`, `height_cm`, `weight_kg`, `age`)
VALUES
(1, 'demo',    '$2a$10$fMFjyjqdh1ds9KZVS6hhfeTDtbQCFpIAtQg0zvAV7maoPvVc97ejm', NULL,  'ADMIN', 1, 'MALE',   178.0, 70.0, 28),
(2, 'other',   '$2a$10$s3xYspM8P05bPJtP/C3Yr.ai8Csux4cYsUrdo.OCH9YSTqTfRp4Tu', NULL,  'USER',  1, 'FEMALE', 165.0, 55.0, 26),
(3, 'allergy', '$2a$10$ds3.OBZqB2WZFIiC4A2.FeCvfMY57cOLKTwOV47E/bXQzAqh4vl5W', 'egg', 'USER',  1, NULL,     NULL,  NULL, NULL);

-- 2) 演示餐次：1/2 属于 demo（1 号是验收用例），3 属于 other
INSERT IGNORE INTO `meal` (`id`, `user_id`, `meal_type`, `eaten_at`) VALUES
(1, 1, 'LUNCH',  '2026-01-01 12:30:00'),
(2, 1, 'DINNER', '2026-01-01 19:00:00'),
(3, 2, 'LUNCH',  '2026-01-01 12:00:00');

-- 3) 餐次食物项（按食物名取 id，并用 NOT EXISTS 保证可重复执行）
INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 1, f.`id`, 200.0 FROM `food` f
WHERE f.`name` = '米饭'
  AND NOT EXISTS (SELECT 1 FROM `meal_item` i WHERE i.`meal_id` = 1 AND i.`food_id` = f.`id`);

INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 1, f.`id`, 150.0 FROM `food` f
WHERE f.`name` = '鸡胸肉'
  AND NOT EXISTS (SELECT 1 FROM `meal_item` i WHERE i.`meal_id` = 1 AND i.`food_id` = f.`id`);

INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 1, f.`id`, 100.0 FROM `food` f
WHERE f.`name` = '西兰花'
  AND NOT EXISTS (SELECT 1 FROM `meal_item` i WHERE i.`meal_id` = 1 AND i.`food_id` = f.`id`);

INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 2, f.`id`, 150.0 FROM `food` f
WHERE f.`name` = '牛里脊'
  AND NOT EXISTS (SELECT 1 FROM `meal_item` i WHERE i.`meal_id` = 2 AND i.`food_id` = f.`id`);

INSERT INTO `meal_item` (`meal_id`, `food_id`, `quantity_g`)
SELECT 3, f.`id`, 200.0 FROM `food` f
WHERE f.`name` = '猪肉白菜饺子'
  AND NOT EXISTS (SELECT 1 FROM `meal_item` i WHERE i.`meal_id` = 3 AND i.`food_id` = f.`id`);

-- 4) 校验
SELECT id, username, role, status, allergens FROM `sys_user` ORDER BY id;
SELECT m.id, m.user_id, u.username, m.meal_type, COUNT(i.id) AS item_count
FROM `meal` m
JOIN `sys_user` u ON u.id = m.user_id
LEFT JOIN `meal_item` i ON i.meal_id = m.id
GROUP BY m.id, m.user_id, u.username, m.meal_type
ORDER BY m.id;

-- =====================================================================
-- Mealwise 迁移 v5：给 meal_item 补上乐观锁列 version
--
-- 背景：实体 `MealItem` 上有 `@Version private Integer version`，
-- MyBatis-Plus 查询会把 version 列带进 SELECT；但历史 init.sql 的 DDL 漏了这列，
-- 结果是**全新安装**后访问 /api/meals/{id}/calories 直接 500：
--   Unknown column 'version' in 'field list'
--
-- 执行一次即可：mysql -u root -p < docs/migration-v5-meal-item-version.sql
-- =====================================================================

USE `mealwise`;

ALTER TABLE `meal_item`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（并发改克数时防丢更新）' AFTER `quantity_g`;

-- 校验
SHOW COLUMNS FROM `meal_item`;

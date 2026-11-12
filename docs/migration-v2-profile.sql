-- =====================================================================
-- Mealwise 迁移 v2：用户身体资料（性别 / 身高 / 体重 / 年龄）
-- 仅需在已有库上执行一次；全新安装直接用 init.sql（已包含这些列）。
-- MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报 1060，可忽略。
-- =====================================================================

USE `mealwise`;

ALTER TABLE `sys_user`
    ADD COLUMN `gender`    VARCHAR(8)   DEFAULT NULL COMMENT 'MALE / FEMALE' AFTER `allergens`,
    ADD COLUMN `height_cm` DECIMAL(5,1) DEFAULT NULL COMMENT '身高 cm'       AFTER `gender`,
    ADD COLUMN `weight_kg` DECIMAL(5,1) DEFAULT NULL COMMENT '体重 kg'       AFTER `height_cm`,
    ADD COLUMN `age`       INT          DEFAULT NULL COMMENT '年龄（可选，缺省按 30 计算）' AFTER `weight_kg`;

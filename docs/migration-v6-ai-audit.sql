-- =====================================================================
-- Mealwise 迁移 v6：AI 调用审计表
--
-- 记录"谁、哪个 AI 能力、走模型还是降级、耗时多少"，不保存用户输入原文。
-- 全新安装直接用 init.sql（已包含本表）；老库执行一次本文件即可。
--
-- 执行：mysql -u root -p < docs/migration-v6-ai-audit.sql
-- =====================================================================

USE `mealwise`;

CREATE TABLE IF NOT EXISTS `ai_audit_log` (
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

SHOW COLUMNS FROM `ai_audit_log`;

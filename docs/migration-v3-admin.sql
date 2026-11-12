-- ============================================================
-- V3：角色与账号状态（管理员）
--
-- 目标：引入最小可用的 RBAC
--   * role   = ADMIN / USER
--   * status = 1 启用 / 0 禁用
--
-- 执行一次即可（幂等：已存在的列会报错，可忽略后重跑 UPDATE）。
-- ============================================================

USE mealwise;

ALTER TABLE sys_user
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER';

ALTER TABLE sys_user
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用';

-- 把演示账号提为管理员（按需替换用户名）
UPDATE sys_user SET role = 'ADMIN', status = 1 WHERE username = 'demo';

-- 校验
SELECT id, username, role, status, created_at FROM sys_user ORDER BY id;

import { http } from '@/api/request'
import type { LoginPayload, LoginResult, RegisterPayload, RegisterResult } from '@/types/api'

/** 注册：用户名唯一，可选设置过敏原 */
export function registerApi(payload: RegisterPayload): Promise<RegisterResult> {
  return http.post<RegisterResult>('/auth/register', payload)
}

/** 登录：返回 JWT 与当前用户信息 */
export function loginApi(payload: LoginPayload): Promise<LoginResult> {
  return http.post<LoginResult>('/auth/login', payload)
}

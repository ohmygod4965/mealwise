import type { UserVO } from '@/types/api'

/**
 * 登录态存储。
 *
 * token 同时保存在 Pinia 与 localStorage：
 * - Pinia 负责页面内的响应式状态；
 * - localStorage 负责刷新页面后的恢复，也供 axios 请求拦截器读取。
 */
const TOKEN_KEY = 'mealwise.token'
const USER_KEY = 'mealwise.user'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function getStoredUser(): UserVO | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    const parsed: unknown = JSON.parse(raw)
    if (
      parsed &&
      typeof parsed === 'object' &&
      'id' in parsed &&
      'username' in parsed &&
      typeof (parsed as UserVO).username === 'string'
    ) {
      return parsed as UserVO
    }
  } catch {
    // 缓存损坏时直接忽略，避免阻塞应用启动
  }
  return null
}

export function saveSession(token: string, user: UserVO): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function saveUser(user: UserVO): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuthStorage(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

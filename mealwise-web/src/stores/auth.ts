import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginApi, registerApi } from '@/api/auth'
import type { LoginPayload, LoginResult, RegisterPayload, RegisterResult, UserVO } from '@/types/api'
import { clearAuthStorage, getStoredUser, getToken, saveSession } from '@/utils/auth-storage'

/**
 * 登录态仓库。
 * token 在 Pinia 中保持响应式，同时通过 utils/auth-storage 同步到 localStorage，
 * 以便刷新页面恢复、以及 axios 请求拦截器读取。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getToken())
  const user = ref<UserVO | null>(getStoredUser())

  const isLoggedIn = computed(() => Boolean(token.value))
  const username = computed(() => user.value?.username ?? '')
  const allergens = computed(() => user.value?.allergens ?? [])
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** 登录成功后写入 Pinia 与 localStorage */
  async function login(payload: LoginPayload): Promise<LoginResult> {
    const result = await loginApi(payload)
    token.value = result.token
    user.value = result.user
    saveSession(result.token, result.user)
    return result
  }

  /** 注册（不自动改变登录态，由页面决定是否紧接着登录） */
  async function register(payload: RegisterPayload): Promise<RegisterResult> {
    return registerApi(payload)
  }

  /** 清空登录态：401 拦截器、退出登录、App 的事件监听都会调用 */
  function reset(): void {
    token.value = ''
    user.value = null
    clearAuthStorage()
  }

  return {
    token,
    user,
    isLoggedIn,
    username,
    allergens,
    isAdmin,
    login,
    register,
    reset
  }
})

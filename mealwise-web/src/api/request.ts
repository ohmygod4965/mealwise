import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { clearAuthStorage, getToken } from '@/utils/auth-storage'

/**
 * 扩展 axios 配置：`silent` 用于后台静默请求（例如恢复上次餐次），
 * 失败时不弹全局错误提示，由调用方自行决定兜底逻辑。
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

const HTTP_FALLBACK_MESSAGES: Record<number, string> = {
  400: '请求参数有误，请检查后重试',
  401: '登录状态已失效，请重新登录',
  403: '没有权限访问该资源',
  404: '请求的资源不存在',
  409: '数据冲突，请检查后重试',
  500: '系统繁忙，请稍后重试'
}

const service = axios.create({
  // 后端接口统一挂在 /api 下；构建时可通过 VITE_API_BASE_URL 覆盖
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 请求拦截器：从 localStorage 读取 token 并注入 Authorization。
 * 不直接依赖 Pinia，避免 request.ts 与 auth store 形成循环依赖。
 */
service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 401 统一处理：清空本地登录态 → 通知应用层重置 Pinia → 跳转登录页 */
function handleUnauthorized(message: string): void {
  clearAuthStorage()
  // 事件解耦：App.vue 监听后调用 authStore.reset()，避免 request 直接 import store
  window.dispatchEvent(new CustomEvent('mealwise:unauthorized'))
  ElMessage.error(message || HTTP_FALLBACK_MESSAGES[401])
  redirectToLogin()
}

/** 使用 hash 路由，直接改 hash 即可跳转，且不依赖 router 实例（避免循环依赖） */
function redirectToLogin(): void {
  const current = window.location.hash.replace(/^#/, '') || '/'
  if (current.startsWith('/login')) return
  window.location.hash = `#/login?redirect=${encodeURIComponent(current)}`
}

/**
 * 响应拦截器：
 * - 业务成功（code === 200）：直接把 data 交给调用方；
 * - 401：清 token 并跳登录；
 * - 403/404/500 及其他业务错误：统一 ElMessage 提示并 reject。
 *
 * `code` 与 HTTP 状态码一致，但为了兼容代理层偶尔返回 200 + 业务错误码的情况，
 * 成功分支仍然显式判断 code。
 */
service.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined
    if (body && body.code === 200) {
      return body.data as unknown as typeof response
    }

    const code = body?.code ?? response.status
    const message = body?.message || HTTP_FALLBACK_MESSAGES[code] || '请求失败，请稍后重试'
    if (code === 401) {
      handleUnauthorized(message)
    } else if (!response.config.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(new Error(message))
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    // 主动取消的请求不提示
    if (axios.isCancel(error)) {
      return Promise.reject(error)
    }

    const status = error.response?.status
    const body = error.response?.data
    const message =
      body?.message ||
      (status ? HTTP_FALLBACK_MESSAGES[status] : '') ||
      (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络连接异常，请检查服务是否可用')

    if (status === 401) {
      handleUnauthorized(message)
    } else if (!error.config?.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(new Error(message))
  }
)

/** 把拦截器已解包的返回值收敛为业务类型 T */
function request<T>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as unknown as Promise<T>
}

/**
 * 统一的请求方法集合。
 * 拦截器已经把 `{ code, message, data }` 解包，因此这里直接返回 Promise<T>。
 */
export const http = {
  get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'GET', params })
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'POST', data })
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'PUT', data })
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'PATCH', data })
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'DELETE' })
  }
}

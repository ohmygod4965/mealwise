import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * Axios 拦截器的四个分支：200 解包、401 清 token 跳登录、403 仅提示、500 兜底文案。
 * 用自定义 adapter 直接喂响应体，不依赖后端。
 */
const errorSpy = vi.fn()
vi.mock('element-plus', () => ({
  ElMessage: { error: (message: string) => errorSpy(message) }
}))

import { http } from './request'
import { getToken, saveSession } from '@/utils/auth-storage'

const user = { id: 1, username: 'demo' } as never

function respond(body: unknown) {
  return async (config: unknown) => ({
    data: body,
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  })
}

function opts(body: unknown) {
  return {
    validateStatus: () => true,
    adapter: respond(body) as never
  }
}

describe('axios 拦截器', () => {
  beforeEach(() => {
    localStorage.clear()
    errorSpy.mockClear()
    window.location.hash = '#/meals'
  })

  it('成功时解包 data', async () => {
    const data = await http.get<{ a: number }>('/x', undefined, opts({ code: 200, data: { a: 1 } }))
    expect(data).toEqual({ a: 1 })
    expect(errorSpy).not.toHaveBeenCalled()
  })

  it('401：清 token、跳登录页、提示', async () => {
    saveSession('token-1', user)
    expect(getToken()).toBe('token-1')

    await expect(
      http.get('/x', undefined, opts({ code: 401, message: '未登录或登录已过期' }))
    ).rejects.toThrow('未登录或登录已过期')

    expect(getToken()).toBe('')
    expect(window.location.hash).toContain('/login')
    expect(errorSpy).toHaveBeenCalledWith('未登录或登录已过期')
  })

  it('403：不跳转，只提示后端 message', async () => {
    saveSession('token-2', user)

    await expect(
      http.get('/x', undefined, opts({ code: 403, message: '无权访问该餐次' }))
    ).rejects.toThrow('无权访问该餐次')

    expect(getToken()).toBe('token-2')
    expect(window.location.hash).toBe('#/meals')
    expect(errorSpy).toHaveBeenCalledWith('无权访问该餐次')
  })

  it('500：无 message 时用兜底文案', async () => {
    await expect(http.get('/x', undefined, opts({ code: 500 }))).rejects.toThrow('系统繁忙，请稍后重试')
    expect(errorSpy).toHaveBeenCalledWith('系统繁忙，请稍后重试')
  })

  it('业务错误优先展示后端 message', async () => {
    await expect(
      http.get('/x', undefined, opts({ code: 400, message: '克数必须大于 0' }))
    ).rejects.toThrow('克数必须大于 0')
    expect(errorSpy).toHaveBeenCalledWith('克数必须大于 0')
  })
})

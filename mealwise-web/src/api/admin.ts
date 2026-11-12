import type { AxiosRequestConfig } from 'axios'
import { http } from '@/api/request'
import type { AdminUserVO, UserRole } from '@/types/api'

/** 用户列表（分页 + 用户名模糊） */
export function listUsers(
  params: { page?: number; size?: number; keyword?: string },
  config?: AxiosRequestConfig
): Promise<{ records: AdminUserVO[]; total: number; page: number; size: number }> {
  return http.get('/admin/users', params, config)
}

/** 启用 / 禁用账号（1=启用，0=禁用） */
export function updateUserStatus(id: number, status: number): Promise<AdminUserVO> {
  return http.patch<AdminUserVO>(`/admin/users/${id}/status`, { status })
}

/** 修改角色 */
export function updateUserRole(id: number, role: UserRole): Promise<AdminUserVO> {
  return http.patch<AdminUserVO>(`/admin/users/${id}/role`, { role })
}

/** 重置密码 */
export function resetUserPassword(id: number, password: string): Promise<null> {
  return http.post<null>(`/admin/users/${id}/reset-password`, { password })
}

/** 删除账号（连带删除其餐次记录） */
export function deleteUser(id: number): Promise<null> {
  return http.delete<null>(`/admin/users/${id}`)
}

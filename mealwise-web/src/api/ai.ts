import type { AxiosRequestConfig } from 'axios'
import { http } from '@/api/request'
import type { AiMealPlanRequest, AiPlanVO } from '@/types/api'

/**
 * 生成 AI 饮食方案。
 *
 * 后端链路：大模型出方案 → Open Food Facts 联网核验热量 → 汇总。
 * 因此超时要比普通接口长（默认 90s），模型慢的时候前端不能先断。
 */
export function generateMealPlan(
  payload: AiMealPlanRequest,
  config?: AxiosRequestConfig
): Promise<AiPlanVO> {
  return http.post<AiPlanVO>('/ai/meal-plan', payload, { timeout: 90000, ...config })
}

import type { AxiosRequestConfig } from 'axios'
import { http } from '@/api/request'
import type { DayRecordVO } from '@/types/api'

/** 今日记录：由后端按服务器当天聚合，无需前端拼日期 */
export function getTodayRecord(config?: AxiosRequestConfig): Promise<DayRecordVO> {
  return http.get<DayRecordVO>('/days/today', undefined, config)
}

/** 指定日期的记录，date 形如 2026-11-07 */
export function getDayRecord(date: string, config?: AxiosRequestConfig): Promise<DayRecordVO> {
  return http.get<DayRecordVO>(`/days/${date}`, undefined, config)
}

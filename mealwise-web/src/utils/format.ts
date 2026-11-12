/** 数字格式化：后端 BigDecimal 统一保留 1 位小数，前端展示时去掉无意义的 .0 */
export function formatNumber(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined) return '—'
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  return num
    .toFixed(digits)
    .replace(/\.0+$/, '')
    .replace(/(\.\d*?)0+$/, '$1')
}

/** 带正负号的热量变化，用于替换建议 */
export function formatDelta(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined) return '—'
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  if (num === 0) return '0'
  return `${num > 0 ? '+' : '−'}${formatNumber(Math.abs(num), digits)}`
}

/**
 * 兼容后端可能返回的 `yyyy-MM-ddTHH:mm:ss` 与 `yyyy-MM-dd HH:mm:ss`。
 * 展示到分钟即可，秒与毫秒对用餐记录没有意义。
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const normalized = value.replace('T', ' ').replace(/\.\d+$/, '')
  return normalized.slice(0, 16)
}

/**
 * 本地日期 → `yyyy-MM-dd`（每日记录接口的路径参数）。
 * 使用本地时区而非 toISOString()，避免晚上 8 点后（UTC+8）被算成前一天。
 */
export function formatDateKey(value: Date = new Date()): string {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** 取用户名首字符作为头像文字 */
export function usernameInitial(username: string): string {
  return (username || '?').trim().charAt(0).toUpperCase() || '?'
}

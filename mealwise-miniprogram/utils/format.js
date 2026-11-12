/**
 * 小程序端格式化工具：日期、数字、餐次文案。
 */

/** 本地时区的 yyyy-MM-dd（不能用 toISOString，它是 UTC 会跨天） */
function todayKey() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return d.getFullYear() + '-' + m + '-' + day
}

/** 'yyyy-MM-dd HH:mm:ss' → 'HH:mm' */
function timeOf(eatenAt) {
  if (!eatenAt || eatenAt.length < 16) return ''
  return eatenAt.substring(11, 16)
}

/** 数字保留 1 位小数（后端已处理，这里只防浮点尾巴） */
function num(value) {
  const n = Number(value || 0)
  return Math.round(n * 10) / 10
}

const MEAL_LABEL = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐',
  SNACK: '加餐'
}

const MEAL_ORDER = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK']

const STATUS_LABEL = {
  UNDER: '未达标',
  ON_TRACK: '达标',
  OVER: '超标'
}

module.exports = { todayKey, timeOf, num, MEAL_LABEL, MEAL_ORDER, STATUS_LABEL }

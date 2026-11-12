const api = require('../../utils/request')
const fmt = require('../../utils/format')

const STATUS_CLASS = { UNDER: 'under', ON_TRACK: 'ok', OVER: 'over' }

Page({
  data: {
    date: fmt.todayKey(),
    isToday: true,
    loading: true,
    error: '',
    totals: null,
    targets: null,
    assumptions: [],
    metrics: [],
    groups: [],
    hasAnyMeal: false
  },

  onShow() {
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const isToday = this.data.date === fmt.todayKey()
      const path = isToday ? '/days/today' : '/days/' + this.data.date
      const record = await api.get(path)
      this.applyRecord(record, isToday)
    } catch (err) {
      this.setData({ error: err.message || '加载失败', loading: false })
    }
  },

  applyRecord(record, isToday) {
    const totals = record.totals || { calories: 0, protein: 0, fat: 0, carb: 0 }

    // 按 早/午/晚/加餐 固定顺序分组，空餐次也要占位，用户才知道"缺哪一餐"
    const groups = fmt.MEAL_ORDER.map((type) => {
      const meals = (record.meals || []).filter((m) => m.mealType === type)
      const calories = meals.reduce((sum, m) => sum + Number(m.total.calories || 0), 0)
      return {
        type,
        label: fmt.MEAL_LABEL[type],
        meals: meals.map((m) => ({
          mealId: m.mealId,
          time: fmt.timeOf(m.eatenAt),
          total: m.total,
          items: m.items
        })),
        calories: fmt.num(calories)
      }
    })

    const metrics = record.comparison
      ? this.buildMetrics(record.comparison)
      : []

    this.setData({
      loading: false,
      isToday,
      totals,
      targets: record.targets,
      assumptions: (record.targets && record.targets.assumptions) || [],
      metrics,
      groups,
      hasAnyMeal: (record.meals || []).length > 0
    })
  },

  buildMetrics(comparison) {
    const defs = [
      { key: 'calories', label: '热量', unit: 'kcal' },
      { key: 'protein', label: '蛋白质', unit: 'g' },
      { key: 'fat', label: '脂肪', unit: 'g' },
      { key: 'carb', label: '碳水', unit: 'g' }
    ]
    return defs.map((def) => {
      const item = comparison[def.key] || { intake: 0, target: 0, diff: 0, percent: 0, status: 'UNDER' }
      const diff = fmt.num(item.diff)
      let diffText = '刚好达标'
      if (diff < 0) diffText = '还差 ' + Math.abs(diff) + ' ' + def.unit
      if (diff > 0) diffText = '已超 ' + diff + ' ' + def.unit
      return {
        key: def.key,
        label: def.label,
        unit: def.unit,
        intake: fmt.num(item.intake),
        target: fmt.num(item.target),
        percent: fmt.num(item.percent),
        // 进度条封顶 100%，避免 300% 撑破卡片
        width: Math.min(100, Math.max(0, fmt.num(item.percent))),
        status: item.status,
        statusClass: STATUS_CLASS[item.status] || 'ok',
        statusLabel: fmt.STATUS_LABEL[item.status] || '',
        diffText
      }
    })
  },

  onDateChange(e) {
    const date = e.detail.value
    this.setData({ date, isToday: date === fmt.todayKey() })
    this.load()
  },

  goToday() {
    this.setData({ date: fmt.todayKey(), isToday: true })
    this.load()
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/profile/profile' })
  },

  /** tabBar 页面不能带参数，用 globalData 传递"想记哪一餐" */
  goMeal(e) {
    const type = e.currentTarget.dataset.type || 'LUNCH'
    getApp().globalData.pendingMealType = type
    wx.switchTab({ url: '/pages/meal/meal' })
  },

  retry() {
    this.load()
  }
})

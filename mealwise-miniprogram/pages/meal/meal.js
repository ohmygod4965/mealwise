const api = require('../../utils/request')
const fmt = require('../../utils/format')

Page({
  data: {
    mealType: 'LUNCH',
    mealTypes: [
      { value: 'BREAKFAST', label: '早餐' },
      { value: 'LUNCH', label: '午餐' },
      { value: 'DINNER', label: '晚餐' },
      { value: 'SNACK', label: '加餐' }
    ],
    mealId: null,
    items: [],
    total: { calories: 0, protein: 0, fat: 0, carb: 0 },
    keyword: '',
    results: [],
    searching: false,
    loading: false,
    adding: false
  },

  onShow() {
    // 从「今日记录」点「去记录早餐」时，把想记的餐次带过来
    const pending = getApp().globalData.pendingMealType
    if (pending) {
      getApp().globalData.pendingMealType = ''
      if (pending !== this.data.mealType) {
        this.setData({ mealType: pending, mealId: null, items: [], results: [] })
        this.resetTotal()
      }
    }
  },

  resetTotal() {
    this.setData({ total: { calories: 0, protein: 0, fat: 0, carb: 0 } })
  },

  onTypeChange(e) {
    const value = e.currentTarget.dataset.value
    if (value === this.data.mealType) return
    const apply = () => {
      // 换餐次等于换一张单：旧餐次已保存，这里从空白开始
      this.setData({ mealType: value, mealId: null, items: [], results: [] })
      this.resetTotal()
    }
    if (this.data.items.length) {
      wx.showModal({
        title: '切换餐次',
        content: '当前这一餐已保存，切换后将开始记录新的餐次。',
        success: (res) => { if (res.confirm) apply() }
      })
      return
    }
    apply()
  },

  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
  },

  async search() {
    if (!this.data.keyword) {
      this.setData({ results: [] })
      return
    }
    this.setData({ searching: true })
    try {
      const result = await api.get('/foods?size=20&keyword=' + encodeURIComponent(this.data.keyword))
      this.setData({ results: result.records || [] })
    } finally {
      this.setData({ searching: false })
    }
  },

  /** 懒创建餐次：只有真正加第一项时才落库，避免产生一堆空餐 */
  async ensureMeal() {
    if (this.data.mealId) return this.data.mealId
    const meal = await api.post('/meals', { mealType: this.data.mealType })
    this.setData({ mealId: meal.id })
    return meal.id
  },

  async addFood(e) {
    const { id, name } = e.currentTarget.dataset
    if (this.data.adding) return
    this.setData({ adding: true })
    try {
      const mealId = await this.ensureMeal()
      await api.post('/meals/' + mealId + '/items', { foodId: id, quantityG: 100 })
      wx.showToast({ title: '已加入 ' + name, icon: 'none' })
      await this.refresh()
    } finally {
      this.setData({ adding: false })
    }
  },

  async refresh() {
    if (!this.data.mealId) return
    this.setData({ loading: true })
    try {
      const result = await api.get('/meals/' + this.data.mealId + '/calories')
      this.setData({ items: result.items || [], total: result.total })
    } finally {
      this.setData({ loading: false })
    }
  },

  /** 改克数：输入框失焦时提交，避免每敲一个字符打一次接口 */
  async onQtyBlur(e) {
    const itemId = e.currentTarget.dataset.id
    const value = Number(e.detail.value)
    if (!value || value <= 0) {
      wx.showToast({ title: '克数必须大于 0', icon: 'none' })
      await this.refresh()
      return
    }
    await api.put('/meals/' + this.data.mealId + '/items/' + itemId, { quantityG: value })
    await this.refresh()
  },

  async removeItem(e) {
    const itemId = e.currentTarget.dataset.id
    const res = await new Promise((resolve) => {
      wx.showModal({ title: '删除', content: '确定删除这一项？', success: resolve })
    })
    if (!res.confirm) return
    await api.del('/meals/' + this.data.mealId + '/items/' + itemId)
    await this.refresh()
  },

  finish() {
    if (!this.data.items.length) {
      wx.showToast({ title: '还没有添加食物', icon: 'none' })
      return
    }
    wx.switchTab({ url: '/pages/day/day' })
  }
})

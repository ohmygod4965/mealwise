const api = require('../../utils/request')

const PAGE_SIZE = 20

Page({
  data: {
    keyword: '',
    category: '',
    list: [],
    page: 1,
    total: 0,
    loading: false,
    finished: false,
    error: ''
  },

  onShow() {
    // 从其它 tab 回来时不重复拉取，保留滚动位置
    if (!this.data.list.length) {
      this.reload()
    }
  },

  onPullDownRefresh() {
    this.reload().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadMore()
  },

  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
  },

  /** 输入框确认 / 搜索按钮：重置到第一页 */
  search() {
    this.reload()
  },

  clearSearch() {
    this.setData({ keyword: '' })
    this.reload()
  },

  async reload() {
    this.setData({ page: 1, list: [], finished: false, error: '' })
    return this.fetch(1)
  },

  async loadMore() {
    if (this.data.loading || this.data.finished) return
    await this.fetch(this.data.page + 1)
  },

  async fetch(page) {
    this.setData({ loading: true })
    try {
      const query = ['page=' + page, 'size=' + PAGE_SIZE]
      if (this.data.keyword) query.push('keyword=' + encodeURIComponent(this.data.keyword))
      if (this.data.category) query.push('category=' + encodeURIComponent(this.data.category))
      const result = await api.get('/foods?' + query.join('&'))
      const records = result.records || []
      const list = page === 1 ? records : this.data.list.concat(records)
      this.setData({
        list,
        page,
        total: result.total,
        finished: list.length >= result.total,
        loading: false
      })
    } catch (err) {
      this.setData({ loading: false, error: err.message || '加载失败' })
    }
  }
})

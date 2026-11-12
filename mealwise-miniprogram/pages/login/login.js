const api = require('../../utils/request')

Page({
  data: {
    mode: 'login', // login | register
    username: '',
    password: '',
    allergens: '',
    loading: false
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [field]: e.detail.value })
  },

  switchMode() {
    this.setData({ mode: this.data.mode === 'login' ? 'register' : 'login' })
  },

  async submit() {
    const { mode, username, password, allergens } = this.data
    if (!username || !password) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }
    // 与后端一致的规则，先在本地拦一次，减少一次无效请求
    if (!/^[a-zA-Z0-9_]{4,50}$/.test(username)) {
      wx.showToast({ title: '用户名需 4-50 位字母/数字/下划线', icon: 'none' })
      return
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(password)) {
      wx.showToast({ title: '密码需 8-32 位且含字母和数字', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      if (mode === 'register') {
        const list = allergens
          .split(',')
          .map((s) => s.trim().toLowerCase())
          .filter((s) => s.length > 0)
        await api.post('/auth/register', { username, password, allergens: list })
        wx.showToast({ title: '注册成功，请登录', icon: 'success' })
        this.setData({ mode: 'login' })
        return
      }

      const result = await api.post('/auth/login', { username, password })
      getApp().saveSession(result.token, result.user)
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => wx.switchTab({ url: '/pages/day/day' }), 400)
    } catch (err) {
      // request.js 已经弹过 toast，这里只兜住 Promise
    } finally {
      this.setData({ loading: false })
    }
  }
})

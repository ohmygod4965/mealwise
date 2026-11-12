// app.js —— 全局配置与登录态
App({
  globalData: {
    // ⚠️ 真机调试时必须改成电脑的局域网 IP（手机与电脑在同一 Wi-Fi 下）
    baseUrl: 'http://10.167.168.18:8080',
    token: '',
    user: null
  },

  onLaunch() {
    const token = wx.getStorageSync('mealwise_token') || ''
    const user = wx.getStorageSync('mealwise_user') || null
    this.globalData.token = token
    this.globalData.user = user
    if (!token) {
      wx.reLaunch({ url: '/pages/login/login' })
    }
  },

  /** 登录成功后统一写入口，避免各页面各写一份 */
  saveSession(token, user) {
    this.globalData.token = token
    this.globalData.user = user
    wx.setStorageSync('mealwise_token', token)
    wx.setStorageSync('mealwise_user', user)
  },

  clearSession() {
    this.globalData.token = ''
    this.globalData.user = null
    wx.removeStorageSync('mealwise_token')
    wx.removeStorageSync('mealwise_user')
  }
})

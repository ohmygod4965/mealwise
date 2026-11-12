/**
 * wx.request 封装：统一 baseUrl、Bearer token、错误提示与 401 跳登录。
 *
 * 返回 Promise<data>：只暴露业务 data，页面里不用再判 code。
 */
function request(path, options) {
  const opts = options || {}
  const app = getApp()
  const header = { 'Content-Type': 'application/json' }
  if (app.globalData.token) {
    header.Authorization = 'Bearer ' + app.globalData.token
  }

  return new Promise(function (resolve, reject) {
    wx.request({
      url: app.globalData.baseUrl + '/api' + path,
      method: opts.method || 'GET',
      data: opts.data,
      header: header,
      success: function (res) {
        const body = res.data || {}
        if (res.statusCode === 200 && body.code === 200) {
          resolve(body.data)
          return
        }
        if (res.statusCode === 401) {
          app.clearSession()
          wx.reLaunch({ url: '/pages/login/login' })
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        const msg = body.message || ('请求失败(' + res.statusCode + ')')
        if (!opts.silent) {
          wx.showToast({ title: msg, icon: 'none', duration: 2200 })
        }
        reject(new Error(msg))
      },
      fail: function (err) {
        if (!opts.silent) {
          wx.showToast({ title: '网络异常，请检查后端地址', icon: 'none', duration: 2200 })
        }
        reject(err)
      }
    })
  })
}

module.exports = {
  get: function (path, options) {
    return request(path, Object.assign({ method: 'GET' }, options))
  },
  post: function (path, data, options) {
    return request(path, Object.assign({ method: 'POST', data: data }, options))
  },
  put: function (path, data, options) {
    return request(path, Object.assign({ method: 'PUT', data: data }, options))
  },
  del: function (path, options) {
    return request(path, Object.assign({ method: 'DELETE' }, options))
  }
}

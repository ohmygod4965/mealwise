const api = require('../../utils/request')

Page({
  data: {
    gender: 'MALE',
    heightCm: '',
    weightKg: '',
    age: '',
    loading: true,
    saving: false
  },

  async onLoad() {
    try {
      const me = await api.get('/users/me')
      this.setData({
        gender: me.gender || 'MALE',
        heightCm: me.heightCm === null || me.heightCm === undefined ? '' : String(me.heightCm),
        weightKg: me.weightKg === null || me.weightKg === undefined ? '' : String(me.weightKg),
        age: me.age === null || me.age === undefined ? '' : String(me.age)
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  onGenderChange(e) {
    this.setData({ gender: e.detail.value })
  },

  onInput(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value })
  },

  async save() {
    const height = Number(this.data.heightCm)
    const weight = Number(this.data.weightKg)
    const age = this.data.age === '' ? undefined : Number(this.data.age)

    // 与后端同一套规则，先在本地拦一次，提示更即时
    if (!height || height < 100 || height > 250) {
      wx.showToast({ title: '身高需在 100-250 cm', icon: 'none' })
      return
    }
    if (!weight || weight < 30 || weight > 300) {
      wx.showToast({ title: '体重需在 30-300 kg', icon: 'none' })
      return
    }
    if (age !== undefined && (age < 10 || age > 100)) {
      wx.showToast({ title: '年龄需在 10-100 岁', icon: 'none' })
      return
    }

    this.setData({ saving: true })
    try {
      await api.put('/users/me/profile', {
        gender: this.data.gender,
        heightCm: height,
        weightKg: weight,
        age
      })
      wx.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 500)
    } finally {
      this.setData({ saving: false })
    }
  }
})

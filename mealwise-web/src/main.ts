import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@/styles/global.css'
// 移动端补丁必须排在 global.css 之后，才能覆盖同等优先级的规则
import '@/styles/mobile.css'
import App from '@/App.vue'
import router from '@/router'

const app = createApp(App)

// Pinia 必须先于 router 安装：路由守卫首次执行时要用到 auth store
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')

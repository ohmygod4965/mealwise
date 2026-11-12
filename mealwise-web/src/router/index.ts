import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录才能访问 */
    requiresAuth?: boolean
    /** 仅未登录时访问（登录/注册页） */
    guestOnly?: boolean
    /** 仅管理员可访问 */
    requiresAdmin?: boolean
    /** 浏览器标题 */
    title?: string
    /** 顶部导航高亮项 */
    nav?: 'foods' | 'meals' | 'day' | 'ai' | 'admin'
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/foods'
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guestOnly: true, title: '注册' }
  },
  {
    path: '/foods',
    name: 'foods',
    component: () => import('@/views/FoodListView.vue'),
    meta: { requiresAuth: true, title: '食物库', nav: 'foods' }
  },
  {
    path: '/meals',
    name: 'meals',
    component: () => import('@/views/MealWorkbenchView.vue'),
    meta: { requiresAuth: true, title: '一餐记录', nav: 'meals' }
  },
  {
    path: '/day',
    name: 'day',
    component: () => import('@/views/DayView.vue'),
    meta: { requiresAuth: true, title: '今日记录', nav: 'day' }
  },
  {
    path: '/ai',
    name: 'ai',
    component: () => import('@/views/AiPlanView.vue'),
    meta: { requiresAuth: true, title: 'AI 饮食推荐', nav: 'ai' }
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true, title: '用户管理', nav: 'admin' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/foods'
  }
]

const router = createRouter({
  // 最终由后端静态托管，使用 hash 模式避免服务端 history fallback
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/**
 * 全局前置守卫：
 * - 未登录访问业务页面 → 跳登录页，并把目标地址放进 redirect，登录后原路返回；
 * - 已登录访问登录/注册页 → 直接进入食物库。
 */
router.beforeEach((to) => {
  const authStore = useAuthStore()
  document.title = `${to.meta.title ?? 'Mealwise'} · Mealwise`

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return {
      name: 'login',
      query: { redirect: to.fullPath }
    }
  }

  if (to.meta.guestOnly && authStore.isLoggedIn) {
    return { name: 'foods' }
  }

  // 管理页只给 ADMIN；非管理员直接送回食物库（服务端同样会 403）
  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    return { name: 'foods' }
  }

  return true
})

export default router

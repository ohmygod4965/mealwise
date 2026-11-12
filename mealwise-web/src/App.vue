<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

// 登录/注册页使用全屏布局，不显示顶部导航
const isAuthPage = computed(() => route.meta.guestOnly === true)

/**
 * request.ts 在 401 时会广播 `mealwise:unauthorized`。
 * 这里同步清空 Pinia，避免路由守卫仍认为处于登录态而把用户弹回业务页。
 */
function handleUnauthorized(): void {
  authStore.reset()
}

onMounted(() => {
  window.addEventListener('mealwise:unauthorized', handleUnauthorized)
})

onBeforeUnmount(() => {
  window.removeEventListener('mealwise:unauthorized', handleUnauthorized)
})
</script>

<template>
  <div class="app-shell">
    <AppHeader v-if="!isAuthPage" />
    <main class="app-main" :class="{ 'app-main--auth': isAuthPage }">
      <RouterView v-slot="{ Component }">
        <Transition name="fade-up" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

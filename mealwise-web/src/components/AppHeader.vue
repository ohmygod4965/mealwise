<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, MagicStick, SwitchButton, User } from '@element-plus/icons-vue'
import AiAssistantDialog from '@/components/AiAssistantDialog.vue'
import ProfileDialog from '@/components/ProfileDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { usernameInitial } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeNav = computed(() => route.meta.nav)
const initial = computed(() => usernameInitial(authStore.username))

/** 顶部导航里的「我的资料」弹窗 */
const profileVisible = ref(false)

/** AI 助手弹窗：录餐 / 对话 / 问答三合一 */
const aiVisible = ref(false)

/** AI 入账成功后广播，让「今日记录」「一餐记录」按需刷新 */
function handleAiSaved(): void {
  window.dispatchEvent(new CustomEvent('mealwise:meal-saved'))
}

/** 退出登录：二次确认后清空 token / 用户信息，并回到登录页 */
async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('退出后需要重新登录才能查看食物库和餐次记录。', '退出登录', {
      confirmButtonText: '退出登录',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch {
    // 用户取消，静默返回
    return
  }

  authStore.reset()
  ElMessage.success('已退出登录')
  await router.replace({ name: 'login' })
}

/** 资料保存成功：广播事件，让「今日记录」等页面重新拉取建议值 */
function handleProfileSaved(): void {
  window.dispatchEvent(new CustomEvent('mealwise:profile-updated'))
}

function handleCommand(command: string): void {
  if (command === 'profile') {
    profileVisible.value = true
    return
  }
  if (command === 'logout') {
    void handleLogout()
  }
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <RouterLink to="/foods" class="brand" aria-label="Mealwise 首页">
        <span class="brand__mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="32" height="32" rx="10" fill="url(#brand-gradient)" />
            <path
              d="M23 8.6c-6.5.4-10.8 4-10.8 9 0 1.6.5 2.9 1.4 3.9 4.9-.7 9.4-4.8 9.4-12.9Z"
              fill="#ffffff"
              fill-opacity="0.95"
            />
            <path
              d="M10 24c1.4-4.2 3.9-6.7 7.6-8.4"
              stroke="#ffffff"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-opacity="0.9"
            />
            <defs>
              <linearGradient id="brand-gradient" x1="2" y1="1" x2="31" y2="32" gradientUnits="userSpaceOnUse">
                <stop stop-color="#3fb37d" />
                <stop offset="1" stop-color="#237b53" />
              </linearGradient>
            </defs>
          </svg>
        </span>
        <span class="brand__text">
          <span class="brand__name">Mealwise</span>
          <span class="brand__tagline">吃得更明白</span>
        </span>
      </RouterLink>

      <nav class="main-nav" aria-label="主导航">
        <RouterLink to="/foods" class="nav-link" :class="{ 'is-active': activeNav === 'foods' }">
          食物库
        </RouterLink>
        <RouterLink to="/meals" class="nav-link" :class="{ 'is-active': activeNav === 'meals' }">
          一餐记录
        </RouterLink>
        <RouterLink to="/day" class="nav-link" :class="{ 'is-active': activeNav === 'day' }">
          今日记录
        </RouterLink>
        <RouterLink to="/ai" class="nav-link nav-link--ai" :class="{ 'is-active': activeNav === 'ai' }">
          AI 推荐
        </RouterLink>
        <RouterLink
          v-if="authStore.isAdmin"
          to="/admin"
          class="nav-link"
          :class="{ 'is-active': activeNav === 'admin' }"
        >
          用户管理
        </RouterLink>
      </nav>

      <div class="header-user">
        <button class="ai-trigger" type="button" @click="aiVisible = true">
          <el-icon><MagicStick /></el-icon>
          <span>AI 助手</span>
        </button>
        <el-dropdown trigger="click" @command="handleCommand">
          <button class="user-button" type="button" aria-label="账号菜单">
            <span class="user-avatar">{{ initial }}</span>
            <span class="user-name">{{ authStore.username || '用户' }}</span>
            <el-icon class="user-caret"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile" :icon="User">我的资料</el-dropdown-item>
              <el-dropdown-item command="logout" :icon="SwitchButton" divided>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </header>

  <!-- 资料弹窗放在 header 外，避免 header 的 backdrop-filter 影响固定定位 -->
  <ProfileDialog v-model="profileVisible" @saved="handleProfileSaved" />
  <AiAssistantDialog v-model="aiVisible" @saved="handleAiSaved" />
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 30;
  border-bottom: 1px solid rgba(227, 235, 230, 0.8);
  background: rgba(250, 252, 250, 0.82);
  backdrop-filter: saturate(140%) blur(14px);
  -webkit-backdrop-filter: saturate(140%) blur(14px);
}

.app-header__inner {
  max-width: 1220px;
  margin: 0 auto;
  padding: 0 28px;
  height: 68px;
  display: flex;
  align-items: center;
  gap: 34px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  color: var(--mw-ink);
  flex-shrink: 0;
}

.brand__mark {
  width: 38px;
  height: 38px;
  display: inline-flex;
  border-radius: 12px;
  box-shadow: 0 8px 18px -10px rgba(35, 123, 83, 0.8);
}

.brand__mark svg {
  width: 100%;
  height: 100%;
  display: block;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand__name {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.brand__tagline {
  margin-top: 3px;
  font-size: 10.5px;
  letter-spacing: 0.13em;
  color: var(--mw-faint);
  font-weight: 600;
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-link {
  position: relative;
  padding: 9px 16px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--mw-muted);
  transition: color 0.18s ease, background 0.18s ease;
}

.nav-link:hover {
  color: var(--mw-primary-dark);
  background: rgba(47, 157, 107, 0.07);
}

.nav-link.is-active {
  color: var(--mw-primary-dark);
  background: var(--mw-primary-soft);
}

.nav-link.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 3px;
  width: 16px;
  height: 2px;
  border-radius: 999px;
  background: var(--mw-primary);
  transform: translateX(-50%);
}

.header-user {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.ai-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border: 1px solid var(--mw-primary-soft, #d9efe3);
  border-radius: 999px;
  background: var(--mw-primary-soft, #eaf6ef);
  color: var(--mw-primary-dark, #1f6b47);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.ai-trigger:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px -18px rgba(27, 63, 46, 0.8);
}

.user-button {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 5px 10px 5px 5px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  color: var(--mw-ink);
  transition: background 0.18s ease, border-color 0.18s ease;
}

.user-button:hover {
  background: #ffffff;
  border-color: var(--mw-line);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  color: #ffffff;
  background: linear-gradient(135deg, #43b985, #237b53);
  box-shadow: 0 4px 10px -6px rgba(35, 123, 83, 0.9);
}

.user-name {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  font-weight: 600;
}

.user-caret {
  font-size: 12px;
  color: var(--mw-faint);
}

@media (max-width: 760px) {
  .app-header__inner {
    padding: 0 16px;
    gap: 12px;
    height: 62px;
  }

  .brand__tagline {
    display: none;
  }

  .brand__name {
    font-size: 16.5px;
  }

  .nav-link {
    padding: 8px 11px;
    font-size: 13.5px;
  }

  .user-name,
  .user-caret {
    display: none;
  }

  .user-button {
    padding: 4px;
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  deleteUser,
  listUsers,
  resetUserPassword,
  updateUserRole,
  updateUserStatus
} from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import type { AdminUserVO, UserRole } from '@/types/api'
import { formatDateTime } from '@/utils/format'

/**
 * 用户管理（仅管理员可见）。
 *
 * 安全约束：改自己那行会被前后端同时拒绝——前端置灰只是体验，服务端才是真正的边界。
 */

const authStore = useAuthStore()

const loading = ref(false)
const rows = ref<AdminUserVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')

const passwordDialogVisible = ref(false)
const passwordTarget = ref<AdminUserVO | null>(null)
const newPassword = ref('')
const savingPassword = ref(false)

const myId = computed(() => authStore.user?.id)

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await listUsers({ page: page.value, size: size.value, keyword: keyword.value.trim() })
    rows.value = data.records
    total.value = data.total
  } catch {
    // 拦截器已提示，这里只保证表格回空态
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function isSelf(row: AdminUserVO): boolean {
  return row.id === myId.value
}

async function toggleStatus(row: AdminUserVO): Promise<void> {
  const next = row.status === 1 ? 0 : 1
  const label = next === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      next === 0
        ? `禁用后「${row.username}」将无法登录，已签发的 token 立即失效。`
        : `确认恢复「${row.username}」的登录权限？`,
      `${label}账号`,
      { type: next === 0 ? 'warning' : 'info', confirmButtonText: label, cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await updateUserStatus(row.id, next)
    ElMessage.success(`已${label}`)
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

async function changeRole(row: AdminUserVO): Promise<void> {
  const next: UserRole = row.role === 'ADMIN' ? 'USER' : 'ADMIN'
  try {
    await ElMessageBox.confirm(
      `把「${row.username}」的角色改为 ${next}？`,
      '修改角色',
      { type: 'warning', confirmButtonText: '确认修改', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await updateUserRole(row.id, next)
    ElMessage.success('角色已更新')
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

function openResetPassword(row: AdminUserVO): void {
  passwordTarget.value = row
  newPassword.value = ''
  passwordDialogVisible.value = true
}

async function submitResetPassword(): Promise<void> {
  if (!passwordTarget.value) return
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(newPassword.value)) {
    ElMessage.warning('密码需 8-32 位且同时包含字母和数字')
    return
  }
  savingPassword.value = true
  try {
    await resetUserPassword(passwordTarget.value.id, newPassword.value)
    ElMessage.success('密码已重置')
    passwordDialogVisible.value = false
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingPassword.value = false
  }
}

async function removeUser(row: AdminUserVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除「${row.username}」将同时删除其全部餐次记录，且不可恢复。`,
      '删除账号',
      { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteUser(row.id)
    ElMessage.success('账号已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

function handlePageChange(next: number): void {
  page.value = next
  void load()
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <header class="admin-head">
      <div>
        <h1>用户管理</h1>
        <p>管理员可以查看全部账号、调整角色与状态、重置密码、删除账号。</p>
      </div>
      <div class="admin-tools">
        <el-input
          v-model="keyword"
          placeholder="按用户名搜索"
          clearable
          class="search-input"
          @keyup.enter="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </header>

    <el-table v-loading="loading" :data="rows" class="admin-table" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="140">
        <template #default="{ row }">
          <span class="username">{{ row.username }}</span>
          <el-tag v-if="isSelf(row)" size="small" type="info" class="self-tag">我</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'success' : 'info'" size="small">
            {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
            {{ row.status === 1 ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="过敏原" min-width="120">
        <template #default="{ row }">
          <span v-if="row.allergens?.length">{{ row.allergens.join('、') }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="资料" width="90">
        <template #default="{ row }">
          <span :class="row.profileComplete ? 'ok' : 'muted'">
            {{ row.profileComplete ? '已完善' : '未完善' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="isSelf(row)" @click="toggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="primary" :disabled="isSelf(row)" @click="changeRole(row)">
            {{ row.role === 'ADMIN' ? '降为用户' : '设为管理员' }}
          </el-button>
          <el-button link type="primary" @click="openResetPassword(row)">重置密码</el-button>
          <el-button link type="danger" :disabled="isSelf(row)" @click="removeUser(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <footer class="admin-foot">
      <span class="muted">共 {{ total }} 个账号</span>
      <el-pagination
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        @current-change="handlePageChange"
      />
    </footer>

    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="420px">
      <p class="dialog-tip">为「{{ passwordTarget?.username }}」设置新密码（8-32 位，含字母和数字）。</p>
      <el-input
        v-model="newPassword"
        type="password"
        show-password
        placeholder="新密码"
        @keyup.enter="submitResetPassword"
      />
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPassword" @click="submitResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 1220px;
  margin: 0 auto;
  padding: 28px 28px 64px;
}

.admin-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.admin-head h1 {
  margin: 0 0 6px;
  font-size: 24px;
  letter-spacing: -0.02em;
  color: var(--mw-ink, #17251f);
}

.admin-head p {
  margin: 0;
  font-size: 13px;
  color: var(--mw-muted, #5b6b62);
}

.admin-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-input {
  width: 220px;
}

.admin-table {
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--mw-line, #e3ebe6);
}

.username {
  font-weight: 600;
  color: var(--mw-ink, #17251f);
}

.self-tag {
  margin-left: 6px;
}

.muted {
  color: var(--mw-faint, #8a9a91);
}

.ok {
  color: #2a7d4f;
}

.admin-foot {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dialog-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--mw-muted, #5b6b62);
}

@media (max-width: 760px) {
  .admin-page {
    padding: 18px 16px 48px;
  }

  .admin-tools {
    width: 100%;
  }

  .search-input {
    flex: 1;
    width: auto;
  }
}
</style>

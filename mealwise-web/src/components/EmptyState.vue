<script setup lang="ts">
withDefaults(
  defineProps<{
    description?: string
    imageSize?: number
  }>(),
  {
    description: '暂无数据',
    imageSize: 96
  }
)
</script>

<template>
  <div class="empty-state">
    <el-empty :image-size="imageSize" :description="description">
      <slot />
    </el-empty>
  </div>
</template>

<style scoped>
.empty-state {
  padding: 18px 0 6px;
}

.empty-state :deep(.el-empty__description p) {
  color: var(--mw-muted);
}
</style>

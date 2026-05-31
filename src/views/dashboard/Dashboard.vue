<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>仪表盘</h2>
      <span style="color: #909399; font-size: 14px;">欢迎回来，{{ authStore.user?.realName || authStore.user?.username }}</span>
    </div>

    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card" @click="$router.push('/knowledge-bases')" style="cursor: pointer;">
            <div class="stat-card__icon" style="background: #ecf5ff; color: #409eff;">
              <el-icon :size="32"><Collection /></el-icon>
            </div>
            <div class="stat-card__info">
              <div class="stat-card__label">知识库</div>
              <div class="stat-card__value">{{ stats.kbCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card" @click="$router.push('/documents')" style="cursor: pointer;">
            <div class="stat-card__icon" style="background: #f0f9eb; color: #67c23a;">
              <el-icon :size="32"><Document /></el-icon>
            </div>
            <div class="stat-card__info">
              <div class="stat-card__label">文档总数</div>
              <div class="stat-card__value">{{ stats.docCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card" @click="$router.push('/review')" style="cursor: pointer;">
            <div class="stat-card__icon" style="background: #fdf6ec; color: #e6a23c;">
              <el-icon :size="32"><Checked /></el-icon>
            </div>
            <div class="stat-card__info">
              <div class="stat-card__label">待审核</div>
              <div class="stat-card__value">{{ stats.pendingReview }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card" @click="$router.push('/qa')" style="cursor: pointer;">
            <div class="stat-card__icon" style="background: #f4f4f5; color: #909399;">
              <el-icon :size="32"><ChatDotRound /></el-icon>
            </div>
            <div class="stat-card__info">
              <div class="stat-card__label">问答次数</div>
              <div class="stat-card__value">{{ stats.qaCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-card style="margin-top: 20px;">
      <template #header><span>快捷入口</span></template>
      <el-row :gutter="12">
        <el-col :span="4" v-for="link in visibleLinks" :key="link.path">
          <div class="quick-link" @click="$router.push(link.path)">
            <el-icon :size="24"><component :is="link.icon" /></el-icon>
            <span>{{ link.label }}</span>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { knowledgeBaseApi } from '@/api/modules/knowledgeBase'
import { fileApi } from '@/api/modules/files'
import { qaApi } from '@/api/modules/qa'
import { Collection, Document, Checked, ChatDotRound, Plus, Search, Setting, User } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const { hasPermission } = usePermissionStore()

const stats = reactive({ kbCount: 0, docCount: 0, pendingReview: 0, qaCount: 0 })

const links = [
  { path: '/knowledge-bases', label: '知识库管理', icon: Collection, perm: 'KB:VIEW' },
  { path: '/documents/upload', label: '上传文档', icon: Plus, perm: 'DOCUMENT:UPLOAD' },
  { path: '/qa', label: '知识问答', icon: Search, perm: 'QA:ASK' },
  { path: '/configs', label: '系统配置', icon: Setting, perm: 'CONFIG:MANAGE' },
  { path: '/users', label: '用户管理', icon: User, perm: 'USER:VIEW' },
]
import { computed } from 'vue'
const visibleLinks = computed(() => links.filter(l => hasPermission(l.perm)))

onMounted(async () => {
  // 仅查询用户有权限的统计数据
  const tasks: Promise<any>[] = []
  tasks.push(knowledgeBaseApi.list({ page: 1, size: 1 }).then(r => stats.kbCount = r.total).catch(() => {}))
  tasks.push(fileApi.list({ page: 1, size: 1 }).then(r => stats.docCount = r.total).catch(() => {}))
  if (hasPermission('QA:HISTORY')) tasks.push(qaApi.getSessions({ page: 1, size: 1 }).then(r => stats.qaCount = r.total).catch(() => {}))
  await Promise.allSettled(tasks)
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-card__icon {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}
.stat-card__label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 4px;
}
.stat-card__value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}
.quick-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.2s;
  font-size: 14px;
  color: #606266;
}
.quick-link:hover {
  background: #f5f7fa;
}
</style>

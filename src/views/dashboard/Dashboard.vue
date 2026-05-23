<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>仪表盘</h2>
    </div>
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in cards" :key="card.title">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-card__icon"><el-icon :size="32"><component :is="card.icon" /></el-icon></div>
            <div class="stat-card__info">
              <div class="stat-card__label">{{ card.title }}</div>
              <div class="stat-card__value">{{ card.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
const cards = [
  { title: '知识库', value: '--', icon: 'Collection' },
  { title: '文档总数', value: '--', icon: 'Document' },
  { title: '待审核', value: '--', icon: 'Checked' },
  { title: '问答次数', value: '--', icon: 'ChatDotRound' },
]
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-card__icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ecf5ff;
  border-radius: 8px;
  color: #409eff;
}
.stat-card__label {
  font-size: 14px;
  color: #909399;
}
.stat-card__value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}
</style>

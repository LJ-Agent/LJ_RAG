<template>
  <div class="tab-bar" v-if="tabStore.tabs.length > 0">
    <div class="tab-list">
      <div
        v-for="tab in tabStore.tabs"
        :key="tab.path"
        :class="['tab-item', { active: tabStore.activeTab === tab.path }]"
        @click="switchTab(tab)"
        @auxclick.prevent="closeTab(tab)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <span class="tab-close" @click.stop="closeTab(tab)">
          <el-icon :size="12"><Close /></el-icon>
        </span>
      </div>
    </div>
    <div class="tab-actions" v-if="tabStore.tabs.length > 0">
      <el-dropdown trigger="click" @command="handleDropdown">
        <span class="tab-dropdown-btn">
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="closeCurrent">关闭当前</el-dropdown-item>
            <el-dropdown-item command="closeOthers">关闭其他</el-dropdown-item>
            <el-dropdown-item command="closeLeft">关闭左侧</el-dropdown-item>
            <el-dropdown-item command="closeRight">关闭右侧</el-dropdown-item>
            <el-dropdown-item command="closeAll" divided style="color:#f56c6c">关闭全部</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Close, ArrowDown } from '@element-plus/icons-vue'
import { useTabStore, type TabItem } from '@/stores/tabs'
import { useRouter } from 'vue-router'

const tabStore = useTabStore()
const router = useRouter()

function switchTab(tab: TabItem) {
  tabStore.setActive(tab.path)
  router.push(tab.fullPath)
}

function closeTab(tab: TabItem) {
  tabStore.removeTab(tab.path)
  // 导航到新的激活标签
  const active = tabStore.tabs.find(t => t.path === tabStore.activeTab)
  if (active) router.push(active.fullPath)
  else if (tabStore.tabs.length > 0) router.push(tabStore.tabs[tabStore.tabs.length - 1].fullPath)
  else router.push('/dashboard')
}

function handleDropdown(cmd: string) {
  const current = tabStore.activeTab
  if (!current) return
  switch (cmd) {
    case 'closeCurrent': tabStore.removeTab(current); break
    case 'closeOthers': tabStore.closeOthers(current); break
    case 'closeLeft': tabStore.closeLeft(current); break
    case 'closeRight': tabStore.closeRight(current); break
    case 'closeAll': tabStore.closeAll(); router.push('/dashboard'); return
  }
  const active = tabStore.tabs.find(t => t.path === tabStore.activeTab)
  if (active) router.push(active.fullPath)
  else if (tabStore.tabs.length > 0) router.push(tabStore.tabs[tabStore.tabs.length - 1].fullPath)
  else router.push('/dashboard')
}
</script>

<style scoped>
.tab-bar {
  display: flex;
  align-items: center;
  height: 36px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 8px;
  flex-shrink: 0;
}
.tab-list {
  display: flex;
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  gap: 2px;
  height: 100%;
}
.tab-list::-webkit-scrollbar { height: 2px; }
.tab-list::-webkit-scrollbar-thumb { background: #d9d9d9; border-radius: 2px; }

.tab-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  height: 28px;
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
  background: #f0f2f5;
  border: 1px solid #e4e7ed;
  border-radius: 4px 4px 0 0;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
  transition: all 0.15s;
  flex-shrink: 0;
}
.tab-item:hover { color: #303133; background: #e8eaed; }
.tab-item.active {
  color: #409eff;
  background: #fff;
  border-bottom-color: #fff;
}

.tab-title { max-width: 120px; overflow: hidden; text-overflow: ellipsis; }

.tab-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px; height: 14px;
  border-radius: 50%;
  color: #909399;
  flex-shrink: 0;
}
.tab-close:hover { background: #c0c4cc; color: #fff; }
.tab-item.active .tab-close:hover { background: #409eff; }

.tab-actions { margin-left: 4px; }
.tab-dropdown-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px; height: 24px;
  border-radius: 2px;
  cursor: pointer;
  color: #909399;
  font-size: 14px;
}
.tab-dropdown-btn:hover { background: #f0f2f5; color: #303133; }
</style>

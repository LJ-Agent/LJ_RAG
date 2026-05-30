import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'

export interface TabItem {
  path: string
  fullPath: string
  title: string
  query?: Record<string, any>
}

export const useTabStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>([])
  const activeTab = ref('')
  const MAX_TABS = 10

  const tabCount = computed(() => tabs.value.length)

  function findTab(path: string): number {
    return tabs.value.findIndex(t => t.path === path)
  }

  function addTab(route: RouteLocationNormalized) {
    // 忽略公开路由和非主布局路由
    const name = (route.name as string) || ''
    if (!name || ['Login', 'Register', '403', '404'].includes(name)) return

    const title = (route.meta?.title as string) || name
    const existing = findTab(route.path)

    if (existing >= 0) {
      // 刷新已存在的标签
      if (route.fullPath !== tabs.value[existing].fullPath) {
        tabs.value[existing] = {
          path: route.path,
          fullPath: route.fullPath,
          title,
          query: { ...route.query },
        }
      }
      activeTab.value = route.path
      return
    }

    // 上限
    if (tabs.value.length >= MAX_TABS) {
      tabs.value.shift()
    }

    tabs.value.push({
      path: route.path,
      fullPath: route.fullPath,
      title,
      query: { ...route.query },
    })
    activeTab.value = route.path
  }

  function removeTab(path: string) {
    const idx = findTab(path)
    if (idx < 0) return
    tabs.value.splice(idx, 1)
    // 如果关闭的是当前标签，激活前一个
    if (activeTab.value === path && tabs.value.length > 0) {
      const next = Math.min(idx, tabs.value.length - 1)
      activeTab.value = tabs.value[next].path
    }
  }

  function setActive(path: string) {
    activeTab.value = path
  }

  function closeOthers(path: string) {
    const t = tabs.value.find(t => t.path === path)
    tabs.value = t ? [t] : []
    activeTab.value = path
  }

  function closeLeft(path: string) {
    const idx = findTab(path)
    if (idx > 0) tabs.value.splice(0, idx)
  }

  function closeRight(path: string) {
    const idx = findTab(path)
    if (idx >= 0 && idx < tabs.value.length - 1) {
      tabs.value.splice(idx + 1)
    }
  }

  function closeAll() {
    tabs.value = []
    activeTab.value = ''
  }

  return {
    tabs, activeTab, tabCount, MAX_TABS,
    addTab, removeTab, setActive,
    closeOthers, closeLeft, closeRight, closeAll,
  }
})

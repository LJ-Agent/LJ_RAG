import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const usePermissionStore = defineStore('permission', () => {
  const permissions = ref<string[]>([])
  const roles = ref<string[]>([])

  const hasPermission = computed(() => (code: string) => permissions.value.includes(code))
  const hasAnyPermission = computed(() => (codes: string[]) => codes.some((c) => permissions.value.includes(c)))
  const hasRole = computed(() => (code: string) => roles.value.includes(code))

  function setPermissions(p: string[]) {
    permissions.value = p
  }

  function setRoles(r: string[]) {
    roles.value = r
  }

  function clearAll() {
    permissions.value = []
    roles.value = []
  }

  return {
    permissions,
    roles,
    hasPermission,
    hasAnyPermission,
    hasRole,
    setPermissions,
    setRoles,
    clearAll,
  }
})

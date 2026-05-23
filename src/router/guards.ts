import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { getAccessToken } from '@/utils/token'
import { loginApi } from '@/api/modules/auth'

export function setupGuards(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    // 公开页面直接放行
    if (to.meta.public) {
      return next()
    }

    const authStore = useAuthStore()
    const permissionStore = usePermissionStore()
    const token = getAccessToken()

    // 无Token → 跳转登录
    if (!token) {
      return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }

    // 有Token但无用户信息 → 拉取
    if (!authStore.user) {
      try {
        const userInfo = await loginApi.getMe()
        authStore.setUser(userInfo)
        permissionStore.setPermissions(userInfo.permissions || [])
        permissionStore.setRoles(userInfo.roles || [])
      } catch {
        authStore.clearAuth()
        permissionStore.clearAll()
        return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
      }
    }

    // 权限检查
    const requiredPerms = to.meta.permissions as string[] | undefined
    if (requiredPerms && requiredPerms.length > 0) {
      const hasAccess = permissionStore.hasAnyPermission(requiredPerms)
      if (!hasAccess) {
        return next('/403')
      }
    }

    next()
  })
}

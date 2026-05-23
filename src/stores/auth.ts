import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserVO } from '@/api/types/auth'
import { getAccessToken, setTokens, removeTokens } from '@/utils/token'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const accessToken = ref<string | null>(getAccessToken())

  const isAuthenticated = computed(() => !!accessToken.value)

  function setAuth(access: string, refresh: string, userInfo: UserVO) {
    accessToken.value = access
    user.value = userInfo
    setTokens(access, refresh)
  }

  function setUser(userInfo: UserVO) {
    user.value = userInfo
  }

  function clearAuth() {
    accessToken.value = null
    user.value = null
    removeTokens()
  }

  return {
    user,
    accessToken,
    isAuthenticated,
    setAuth,
    setUser,
    clearAuth,
  }
})

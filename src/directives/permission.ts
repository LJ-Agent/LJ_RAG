import type { Directive } from 'vue'
import { usePermissionStore } from '@/stores/permission'

export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const { hasAnyPermission } = usePermissionStore()
    const codes = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!hasAnyPermission(codes)) {
      el.parentNode?.removeChild(el)
    }
  },
}

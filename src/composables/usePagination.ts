import { ref, reactive } from 'vue'

export function usePagination(defaultSize = 20) {
  const page = ref(1)
  const size = ref(defaultSize)
  const total = ref(0)

  const params = reactive({
    page: 1,
    size: defaultSize,
  })

  function onPageChange(p: number) {
    page.value = p
    params.page = p
  }

  function onSizeChange(s: number) {
    size.value = s
    params.size = s
    page.value = 1
    params.page = 1
  }

  function reset() {
    page.value = 1
    params.page = 1
    total.value = 0
  }

  function setTotal(t: number) {
    total.value = t
  }

  return { page, size, total, params, onPageChange, onSizeChange, reset, setTotal }
}

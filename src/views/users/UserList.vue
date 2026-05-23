<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>用户管理</h2>
    </div>

    <el-table :data="list" v-loading="isLoading" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="realName" label="真实姓名" width="120" />
      <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="USER_STATUS_MAP[row.status]?.type || 'info'" size="small">
            {{ USER_STATUS_MAP[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <el-tag v-for="role in row.roles" :key="role" size="small" style="margin-right: 4px;">{{ role }}</el-tag>
          <span v-if="!row.roles?.length" style="color:#c0c4cc">-</span>
        </template>
      </el-table-column>
      <el-table-column label="最后登录" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openRoles(row)" v-permission="'USER:UPDATE'">管理角色</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pagination.params.page"
        v-model:page-size="pagination.params.size"
        :total="pagination.total.value"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="pagination.onPageChange"
        @size-change="pagination.onSizeChange"
      />
    </div>

    <!-- 角色管理弹窗 -->
    <el-dialog v-model="rolesVisible" title="管理用户角色" width="520px" destroy-on-close>
      <div v-if="currentUser">
        <div class="roles-section">
          <h4>当前角色</h4>
          <el-tag
            v-for="role in currentRoles"
            :key="role.id"
            closable
            style="margin: 0 8px 8px 0"
            @close="removeRole(role.id)"
          >
            {{ role.roleName }} ({{ role.roleCode }})
          </el-tag>
          <span v-if="currentRoles.length === 0" style="color:#c0c4cc">暂无角色</span>
        </div>
        <el-divider />
        <div class="roles-section">
          <h4>分配角色</h4>
          <el-select v-model="assignRoleId" placeholder="选择角色" style="width: 240px">
            <el-option v-for="role in availableRoles" :key="role.id" :label="`${role.roleName} (${role.roleCode})`" :value="role.id" />
          </el-select>
          <el-button type="primary" style="margin-left: 12px" :loading="assigning" @click="assignRole">分配</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { userApi } from '@/api/modules/users'
import { usePagination } from '@/composables/usePagination'
import { formatDate } from '@/utils/format'
import { USER_STATUS_MAP } from '@/utils/constants'
import type { UserVO } from '@/api/types/user'
import type { RoleVO } from '@/api/types/user'
import { ElMessage } from 'element-plus'

const pagination = usePagination()
const list = ref<UserVO[]>([])
const isLoading = ref(false)

const rolesVisible = ref(false)
const currentUser = ref<UserVO | null>(null)
const currentRoles = ref<RoleVO[]>([])
const assignRoleId = ref<number | null>(null)
const assigning = ref(false)

// 简化处理：从当前已知角色中排除已分配
const availableRoles = ref<RoleVO[]>([])
const allRoles: RoleVO[] = [
  { id: 1, roleName: '超级管理员', roleCode: 'SUPER_ADMIN', description: '', status: 1 },
  { id: 2, roleName: '管理员', roleCode: 'ADMIN', description: '', status: 1 },
  { id: 3, roleName: '审核员', roleCode: 'REVIEWER', description: '', status: 1 },
  { id: 4, roleName: '编辑者', roleCode: 'EDITOR', description: '', status: 1 },
  { id: 5, roleName: '查看者', roleCode: 'VIEWER', description: '', status: 1 },
]

async function fetchList() {
  isLoading.value = true
  try {
    const res = await userApi.list({
      page: pagination.params.page,
      size: pagination.params.size,
    })
    list.value = res.records
    pagination.setTotal(res.total)
  } finally {
    isLoading.value = false
  }
}

async function openRoles(user: UserVO) {
  currentUser.value = user
  try {
    const roles = await userApi.getUserRoles(user.id)
    currentRoles.value = roles
  } catch {
    currentRoles.value = []
  }
  availableRoles.value = allRoles.filter(
    (r) => !currentRoles.value.some((cr) => cr.id === r.id)
  )
  assignRoleId.value = null
  rolesVisible.value = true
}

async function assignRole() {
  if (!assignRoleId.value || !currentUser.value) return
  assigning.value = true
  try {
    await userApi.assignRole(currentUser.value.id, assignRoleId.value)
    ElMessage.success('角色分配成功')
    // 刷新角色列表
    const roles = await userApi.getUserRoles(currentUser.value.id)
    currentRoles.value = roles
    availableRoles.value = allRoles.filter(
      (r) => !currentRoles.value.some((cr) => cr.id === r.id)
    )
    assignRoleId.value = null
  } finally {
    assigning.value = false
  }
}

async function removeRole(roleId: number) {
  if (!currentUser.value) return
  try {
    await userApi.removeRole(currentUser.value.id, roleId)
    ElMessage.success('角色移除成功')
    const roles = await userApi.getUserRoles(currentUser.value.id)
    currentRoles.value = roles
    availableRoles.value = allRoles.filter(
      (r) => !currentRoles.value.some((cr) => cr.id === r.id)
    )
  } catch { /* ignore */ }
}

watch(pagination.params, () => fetchList(), { immediate: true })
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.roles-section h4 {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 12px;
  color: #303133;
}
</style>

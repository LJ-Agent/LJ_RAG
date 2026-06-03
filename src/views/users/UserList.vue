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
      <el-table-column label="所属团队" min-width="160">
        <template #default="{ row }">
          <template v-if="getUserTeam(row.id)">
            <el-tag size="small" type="warning">{{ getUserTeam(row.id)?.teamName }}</el-tag>
            <el-tag size="small" :type="roleTagType(getUserTeam(row.id)?.roleCode)" style="margin-left:4px">
              {{ roleLabel(getUserTeam(row.id)?.roleCode) }}
            </el-tag>
          </template>
          <span v-else style="color:#c0c4cc">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="最后登录" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openRoles(row)" v-permission="'USER:UPDATE'">管理角色</el-button>
          <el-button link type="warning" size="small" @click="openTeam(row)" v-permission="'USER:UPDATE'">管理团队</el-button>
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

    <!-- 团队管理弹窗 -->
    <el-dialog v-model="teamVisible" title="管理用户团队" width="520px" destroy-on-close>
      <div v-if="currentUser">
        <div class="roles-section">
          <h4>当前团队</h4>
          <template v-if="getUserTeam(currentUser.id)">
            <el-tag type="warning" size="small">{{ getUserTeam(currentUser.id)?.teamName }}</el-tag>
            <el-tag size="small" style="margin-left:6px">{{ roleLabel(getUserTeam(currentUser.id)?.roleCode) }}</el-tag>
          </template>
          <span v-else style="color:#c0c4cc">未分配团队</span>
        </div>
        <el-divider />
        <div class="roles-section">
          <h4>分配团队</h4>
          <el-select v-model="assignTeamId" placeholder="选择团队" style="width:200px" @change="onTeamChange">
            <el-option v-for="t in allTeams" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <el-select v-model="assignRoleCode" placeholder="团队角色" style="width:150px;margin-left:8px" v-if="assignTeamId">
            <el-option label="所有者" value="team_owner" />
            <el-option label="管理员" value="team_admin" />
            <el-option label="编辑者" value="team_editor" />
            <el-option label="审核者" value="team_reviewer" />
            <el-option label="访客" value="team_viewer" />
          </el-select>
          <el-button type="primary" style="margin-left:12px" :loading="teamAssigning" @click="assignTeam" :disabled="!assignTeamId || !assignRoleCode">分配</el-button>
        </div>
        <el-divider />
        <div class="roles-section">
          <h4>移除</h4>
          <el-button v-if="getUserTeam(currentUser.id)" type="danger" plain size="small" @click="removeFromTeam">从团队移除</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { userApi } from '@/api/modules/users'
import { usePagination } from '@/composables/usePagination'
import { formatDate } from '@/utils/format'
import { USER_STATUS_MAP } from '@/utils/constants'
import type { UserVO } from '@/api/types/user'
import type { RoleVO } from '@/api/types/user'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const pagination = usePagination()
const list = ref<UserVO[]>([])
const isLoading = ref(false)

// 团队-用户映射
const userTeamMap = ref<Record<number, { teamName: string; roleCode: string }>>({})

const ROLE_LABELS: Record<string, string> = {
  team_owner: '所有者', team_admin: '管理员', team_editor: '编辑者',
  team_reviewer: '审核者', team_viewer: '访客'
}
function roleLabel(code: string | undefined) { return ROLE_LABELS[code || ''] || code || '未知' }
function roleTagType(code: string | undefined) {
  if (code === 'team_owner') return 'danger'
  if (code === 'team_admin') return 'warning'
  if (code === 'team_editor') return 'success'
  return 'info'
}
function getUserTeam(userId: number) { return userTeamMap.value[userId] || null }

async function loadTeams() {
  try {
    // 拉取所有团队+成员
    const myTeams = await request.get('/teams')
    const allMembers: any[] = []
    const teamNames: Record<number, string> = {}
    for (const t of (myTeams as any[] || [])) {
      teamNames[t.team.id] = t.team.name
    }
    // 需要单独拉每个团队的成员 — 简化: 仅显示自己的团队信息
    // 更好的做法: 后端提供一个 /api/teams/all-members 接口
    // 当前简化: admin 查看默认团队所有成员
    for (const t of (myTeams as any[] || [])) {
      try {
        const members = await request.get(`/teams/${t.team.id}/members`)
        for (const m of (members as any[] || [])) {
          userTeamMap.value[m.userId] = { teamName: t.team.name, roleCode: m.roleCode }
        }
      } catch {}
    }
  } catch {}
}

onMounted(async () => { await loadTeams() })

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

// ─── 团队管理 ───
const teamVisible = ref(false)
const allTeams = ref<any[]>([])
const assignTeamId = ref<number | null>(null)
const assignRoleCode = ref('team_viewer')
const teamAssigning = ref(false)

async function fetchAllTeams() {
  try {
    const myTeams = await request.get('/teams')
    // 管理员需要所有团队 — 简化: 遍历已知团队
    allTeams.value = (myTeams as any[]).map((t: any) => t.team)
  } catch { allTeams.value = [] }
}

async function openTeam(user: UserVO) {
  currentUser.value = user
  assignTeamId.value = null
  assignRoleCode.value = 'team_viewer'
  await fetchAllTeams()
  teamVisible.value = true
}

function onTeamChange(_val: number) { /* role selection stays */ }

async function assignTeam() {
  if (!assignTeamId.value || !currentUser.value) return
  teamAssigning.value = true
  try {
    // 先移除旧团队
    const existing = getUserTeam(currentUser.value.id)
    if (existing) {
      for (const t of allTeams.value) {
        if (t.name === existing.teamName) {
          await request.delete(`/teams/${t.id}/members/${currentUser.value.id}`)
          break
        }
      }
    }
    // 加入新团队
    await request.post(`/teams/${assignTeamId.value}/members`, {
      userId: currentUser.value.id,
      roleCode: assignRoleCode.value,
    })
    ElMessage.success('团队分配成功')
    teamVisible.value = false
    await loadTeams()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '分配失败')
  } finally { teamAssigning.value = false }
}

async function removeFromTeam() {
  if (!currentUser.value) return
  const existing = getUserTeam(currentUser.value.id)
  if (!existing) return
  try {
    for (const t of allTeams.value) {
      if (t.name === existing.teamName) {
        await request.delete(`/teams/${t.id}/members/${currentUser.value.id}`)
        ElMessage.success('已从团队移除')
        teamVisible.value = false
        await loadTeams()
        return
      }
    }
  } catch { ElMessage.error('移除失败') }
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

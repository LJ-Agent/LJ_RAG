<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>团队管理</h2>
      <el-button type="primary" @click="openCreate" v-permission="'CONFIG:MANAGE'">
        <el-icon><Plus /></el-icon> 新建团队
      </el-button>
    </div>

    <el-table :data="teamList" v-loading="loading" stripe border>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="name" label="团队名称" min-width="160" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="成员数" width="80" align="center">
        <template #default="{ row }">{{ memberCounts[row.id] || 0 }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openMembers(row)">成员</el-button>
          <el-button link type="warning" size="small" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确定删除该团队？团队成员将被移除" @confirm="handleDelete(row.id)">
            <template #reference><el-button link type="danger" size="small">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑团队' : '新建团队'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="团队名称" prop="name"><el-input v-model="form.name" placeholder="输入团队名称" /></el-form-item>
        <el-form-item label="描述" prop="description"><el-input v-model="form.description" type="textarea" :rows="2" placeholder="团队描述" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button></template>
    </el-dialog>

    <!-- 成员管理弹窗 -->
    <el-dialog v-model="membersVisible" :title="'团队成员 — ' + currentTeam?.name" width="640px" destroy-on-close>
      <!-- 现有成员 -->
      <el-table :data="members" stripe border size="small" style="margin-bottom:16px">
        <el-table-column label="用户ID" width="70" prop="userId" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-select v-model="row.roleCode" size="small" @change="(v: string) => updateRole(row, v)">
              <el-option label="所有者" value="team_owner" />
              <el-option label="管理员" value="team_admin" />
              <el-option label="编辑者" value="team_editor" />
              <el-option label="审核者" value="team_reviewer" />
              <el-option label="访客" value="team_viewer" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" min-width="160" prop="joinedAt">
          <template #default="{ row }">{{ formatDate(row.joinedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row }">
            <el-popconfirm title="确定移除此成员？" @confirm="removeMember(row)">
              <template #reference><el-button link type="danger" size="small">移除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 添加成员 -->
      <el-divider content-position="left">添加成员</el-divider>
      <div style="display:flex;gap:8px">
        <el-select v-model="addUserId" placeholder="选择用户" filterable style="flex:1">
          <el-option v-for="u in availableUsers" :key="u.id" :label="`${u.username} (${u.realName || '-'})`" :value="u.id" />
        </el-select>
        <el-select v-model="addRoleCode" placeholder="角色" style="width:120px">
          <el-option label="所有者" value="team_owner" />
          <el-option label="管理员" value="team_admin" />
          <el-option label="编辑者" value="team_editor" />
          <el-option label="审核者" value="team_reviewer" />
          <el-option label="访客" value="team_viewer" />
        </el-select>
        <el-button type="primary" :loading="adding" @click="addMember" :disabled="!addUserId">添加</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/format'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/api/request'

const teamList = ref<any[]>([])
const loading = ref(false)
const memberCounts = ref<Record<number, number>>({})

async function fetchTeams() {
  loading.value = true
  try {
    const data = await request.post('/teams')
    teamList.value = (data as any[]).map((t: any) => ({ ...t.team, role: t.role }))
    // 拉成员数
    for (const t of teamList.value) {
      try {
        const members = await request.post(`/teams/${t.id}/members`)
        memberCounts.value[t.id] = (members as any[]).length
      } catch { memberCounts.value[t.id] = 0 }
    }
  } finally { loading.value = false }
}

// ─── 创建/编辑 ───
const dialogVisible = ref(false), isEdit = ref(false), submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ id: 0, name: '', description: '' })
const rules: FormRules = { name: [{ required: true, message: '请输入团队名称' }] }
function openCreate() { isEdit.value = false; form.id = 0; form.name = ''; form.description = ''; dialogVisible.value = true }
function openEdit(row: any) { isEdit.value = true; form.id = row.id; form.name = row.name; form.description = row.description || ''; dialogVisible.value = true }
async function handleSubmit() {
  const v = await formRef.value?.validate().catch(() => false); if (!v) return
  submitting.value = true
  try {
    if (isEdit.value) await request.put(`/teams/${form.id}`, { name: form.name, description: form.description })
    else await request.post('/teams', { name: form.name, description: form.description })
    ElMessage.success(isEdit.value ? '已更新' : '已创建')
    dialogVisible.value = false; fetchTeams()
  } catch { ElMessage.error('操作失败') } finally { submitting.value = false }
}
async function handleDelete(id: number) { await request.delete(`/teams/${id}`); ElMessage.success('已删除'); fetchTeams() }

// ─── 成员管理 ───
import { reactive } from 'vue'
const membersVisible = ref(false), currentTeam = ref<any>(null)
const members = ref<any[]>([])
const addUserId = ref<number | null>(null), addRoleCode = ref('team_viewer'), adding = ref(false)
const allUsers = ref<any[]>([])

const availableUsers = computed(() => {
  const memberIds = members.value.map((m: any) => m.userId)
  return allUsers.value.filter((u: any) => !memberIds.includes(u.id))
})

async function openMembers(team: any) {
  currentTeam.value = team; membersVisible.value = true; addUserId.value = null
  try {
    members.value = await request.post(`/teams/${team.id}/members`)
    allUsers.value = await request.post('/users', { page: 1, size: 200 }).then((r: any) => r.records || [])
  } catch { members.value = []; allUsers.value = [] }
}
async function addMember() {
  if (!addUserId.value) return; adding.value = true
  try { await request.post(`/teams/${currentTeam.value.id}/members`, { userId: addUserId.value, roleCode: addRoleCode.value }); ElMessage.success('已添加'); addUserId.value = null; openMembers(currentTeam.value) } catch { ElMessage.error('添加失败') } finally { adding.value = false }
}
async function removeMember(row: any) { await request.delete(`/teams/${currentTeam.value.id}/members/${row.userId}`); ElMessage.success('已移除'); openMembers(currentTeam.value) }
async function updateRole(row: any, newRole: string) { await request.put(`/teams/${currentTeam.value.id}/members/${row.userId}`, { roleCode: newRole }); ElMessage.success('角色已更新') }

import { computed } from 'vue'

onMounted(() => fetchTeams())
</script>

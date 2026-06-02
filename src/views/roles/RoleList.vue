<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>角色与权限管理</h2>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- 系统角色 -->
      <el-tab-pane label="系统角色" name="system">
        <el-button type="primary" @click="openCreate('system')" v-permission="'CONFIG:MANAGE'" style="margin-bottom:12px">
          <el-icon><Plus /></el-icon> 新建角色
        </el-button>
        <el-table :data="sysRoles" stripe border v-loading="sysLoading">
          <el-table-column prop="roleCode" label="角色编码" width="150" />
          <el-table-column prop="roleName" label="角色名称" width="120" />
          <el-table-column prop="description" label="描述" min-width="200" />
          <el-table-column label="权限" min-width="300">
            <template #default="{row}">
              <el-tag v-for="p in row.permissions" :key="p" size="small" style="margin:2px">{{ p }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template #default="{row}">
              <el-button link type="primary" size="small" @click="openPerms(row,'system')">权限</el-button>
              <el-button link type="warning" size="small" @click="openEdit(row,'system')">编辑</el-button>
              <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id,'system')">
                <template #reference><el-button link type="danger" size="small">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 团队角色 -->
      <el-tab-pane label="团队角色" name="team">
        <el-button type="primary" @click="openCreate('team')" v-permission="'CONFIG:MANAGE'" style="margin-bottom:12px">
          <el-icon><Plus /></el-icon> 新建团队角色
        </el-button>
        <el-table :data="teamRoles" stripe border v-loading="teamLoading">
          <el-table-column prop="role_code" label="角色编码" width="150" />
          <el-table-column prop="role_name" label="角色名称" width="120" />
          <el-table-column prop="description" label="描述" min-width="200" />
          <el-table-column label="权限数" width="80" align="center">
            <template #default="{row}"><el-tag size="small" type="warning">{{ row.perm_count }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template #default="{row}">
              <el-button link type="primary" size="small" @click="openPerms(row,'team')">权限</el-button>
              <el-button link type="warning" size="small" @click="openEdit(row,'team')">编辑</el-button>
              <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id,'team')">
                <template #reference><el-button link type="danger" size="small">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新建角色'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="角色编码" required><el-input v-model="form.code" placeholder="如 DATA_ANALYST" :disabled="isEdit" /></el-form-item>
        <el-form-item label="角色名称" required><el-input v-model="form.name" placeholder="如 数据分析师" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.desc" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配弹窗 -->
    <el-dialog v-model="permVisible" :title="'分配权限 — ' + currentRoleName" width="600px" destroy-on-close>
      <el-checkbox-group v-model="selectedPerms">
        <div v-for="(codes, group) in allPerms" :key="group" style="margin-bottom:12px">
          <div style="font-weight:600;margin-bottom:4px">{{ group }}</div>
          <el-checkbox v-for="c in codes" :key="c" :value="c" :label="c" style="margin-right:12px;margin-bottom:4px">{{ c }}</el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permVisible=false">取消</el-button>
        <el-button type="primary" @click="handleSavePerms">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const activeTab = ref('system')
const sysRoles = ref<any[]>([]); const teamRoles = ref<any[]>([])
const sysLoading = ref(false); const teamLoading = ref(false)
const allPerms = ref<Record<string,string[]>>({})

const dialogVisible = ref(false); const isEdit = ref(false); const formType = ref('system')
const form = ref({ id:0, code:'', name:'', desc:'' })
const permVisible = ref(false); const currentRoleId = ref(0)
const currentRoleType = ref('system'); const currentRoleName = ref('')
const selectedPerms = ref<string[]>([])

// ID↔Code 映射表 (从 /api/permissions/flat 动态获取)
const permIdToCode = ref<Record<number,string>>({})
const permCodeToId = ref<Record<string,number>>({})

async function fetchPermMap() {
  try {
    const flat: any[] = await request.get('/permissions/flat')
    for (const p of flat) {
      permIdToCode.value[p.id] = p.permissionCode
      permCodeToId.value[p.permissionCode] = p.id
    }
  } catch {}
}

async function fetchSysRoles() {
  sysLoading.value = true
  try {
    const page = await request.get('/roles')
    const records = page.records || []
    for (const r of records) {
      try {
        const detail = await request.get(`/roles/${r.id}`)
        const pids: number[] = detail.permissionIds || []
        r.permissions = pids.map((id:number) => permIdToCode.value[id] || String(id))
      } catch { r.permissions = [] }
    }
    sysRoles.value = records
  } finally { sysLoading.value = false }
}

async function fetchTeamRoles() {
  teamLoading.value = true
  try { teamRoles.value = await request.get('/team-roles') || [] }
  finally { teamLoading.value = false }
}

async function fetchPerms() {
  try {
    const data = await request.get('/permissions')
    // data = {SYSTEM: [{permissionCode,...}], QA: [...], ...}
    const grouped: Record<string,string[]> = {}
    for (const [group, perms] of Object.entries(data)) {
      grouped[group] = (perms as any[]).map((p:any) => p.permissionCode)
    }
    allPerms.value = grouped
  } catch {}
}

function openCreate(type:string) { isEdit.value=false; formType.value=type; form.value={id:0,code:'',name:'',desc:''}; dialogVisible.value=true }
function openEdit(row:any,type:string) { isEdit.value=true; formType.value=type; form.value={id:row.id, code:type==='system'?row.roleCode:row.role_code, name:type==='system'?row.roleName:row.role_name, desc:row.description||''}; dialogVisible.value=true }

async function handleSave() {
  const url = formType.value==='system' ? '/roles' : '/team-roles'
  const body = formType.value==='system'
    ? {roleCode:form.value.code,roleName:form.value.name,description:form.value.desc}
    : {roleCode:form.value.code,roleName:form.value.name,description:form.value.desc}
  try {
    if(isEdit.value) await request.put(`${url}/${form.value.id}`, body)
    else await request.post(url, body)
    ElMessage.success(isEdit.value?'已更新':'已创建'); dialogVisible.value=false
    formType.value==='system' ? fetchSysRoles() : fetchTeamRoles()
  } catch { ElMessage.error('操作失败') }
}

async function handleDelete(id:number,type:string) {
  const url = type==='system' ? `/roles/${id}` : `/team-roles/${id}`
  try { await request.delete(url); ElMessage.success('已删除'); type==='system'?fetchSysRoles():fetchTeamRoles() } catch { ElMessage.error('删除失败') }
}

async function openPerms(row:any,type:string) {
  currentRoleId.value = row.id; currentRoleType.value = type
  currentRoleName.value = type==='system' ? row.roleName : row.role_name
  selectedPerms.value = []
  try {
    if(type==='system') {
      const detail = await request.get(`/roles/${row.id}`)
      const pids: number[] = detail.permissionIds || []
      selectedPerms.value = pids.map((id:number) => permIdToCode.value[id] || String(id))
    } else {
      selectedPerms.value = await request.get(`/team-roles/${row.id}/permissions`) || []
    }
  } catch { selectedPerms.value = [] }
  permVisible.value = true
}

async function handleSavePerms() {
  const type = currentRoleType.value; const id = currentRoleId.value
  try {
    if(type==='system') {
      const ids = selectedPerms.value.map((c:string) => permCodeToId.value[c]).filter(Boolean)
      await request.put(`/roles/${id}/permissions`, {permissionIds:ids})
    } else {
      await request.put(`/team-roles/${id}/permissions`, {permissionCodes:selectedPerms.value})
    }
    ElMessage.success('权限已更新'); permVisible.value = false
    type==='system' ? fetchSysRoles() : fetchTeamRoles()
  } catch { ElMessage.error('保存失败') }
}

onMounted(() => { fetchPermMap().then(() => { fetchSysRoles(); fetchTeamRoles(); fetchPerms() }) })
</script>

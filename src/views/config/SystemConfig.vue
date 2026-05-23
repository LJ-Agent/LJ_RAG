<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>系统配置</h2>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 添加配置
      </el-button>
    </div>

    <el-table :data="list" v-loading="isLoading" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="configKey" label="配置键" min-width="220" show-overflow-tooltip />
      <el-table-column prop="configValue" label="配置值" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.configType === 'BOOLEAN'">
            <el-tag :type="row.configValue === 'true' ? 'success' : 'danger'" size="small">{{ row.configValue }}</el-tag>
          </span>
          <span v-else>{{ row.configValue }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="configType" label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ CONFIG_TYPE_MAP[row.configType] || row.configType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
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

    <!-- 添加/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑配置' : '添加配置'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如 review.auto_approve_hours" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="输入配置值" />
        </el-form-item>
        <el-form-item label="类型" prop="configType">
          <el-select v-model="form.configType" style="width: 100%">
            <el-option v-for="t in ['STRING', 'NUMBER', 'BOOLEAN', 'JSON']" :key="t" :label="CONFIG_TYPE_MAP[t]" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="配置说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { configApi } from '@/api/modules/configs'
import { usePagination } from '@/composables/usePagination'
import { CONFIG_TYPE_MAP } from '@/utils/constants'
import type { SystemConfigVO } from '@/api/types/config'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'

const pagination = usePagination(50)
const list = ref<SystemConfigVO[]>([])
const isLoading = ref(false)
const submitting = ref(false)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: 0,
  configKey: '',
  configValue: '',
  configType: 'STRING' as SystemConfigVO['configType'],
  description: '',
})

const rules: FormRules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }],
  configType: [{ required: true, message: '请选择类型', trigger: 'change' }],
}

async function fetchList() {
  isLoading.value = true
  try {
    const res = await configApi.list({
      page: pagination.params.page,
      size: pagination.params.size,
    })
    list.value = res.records
    pagination.setTotal(res.total)
  } finally {
    isLoading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.id = 0
  form.configKey = ''
  form.configValue = ''
  form.configType = 'STRING'
  form.description = ''
  dialogVisible.value = true
}

function openEdit(row: SystemConfigVO) {
  isEdit.value = true
  form.id = row.id
  form.configKey = row.configKey
  form.configValue = row.configValue
  form.configType = row.configType
  form.description = row.description
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await configApi.save({
      configKey: form.configKey,
      configValue: form.configValue,
      configType: form.configType,
      description: form.description,
    })
    ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  await configApi.delete(id)
  ElMessage.success('删除成功')
  fetchList()
}

watch(pagination.params, () => fetchList(), { immediate: true })
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

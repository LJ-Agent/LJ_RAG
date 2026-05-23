<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>知识库管理</h2>
      <el-button type="primary" @click="openCreate" v-permission="'KB:CREATE'">
        <el-icon><Plus /></el-icon> 创建知识库
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="知识库名称">
          <el-input v-model="query.kbName" placeholder="输入名称搜索" clearable @clear="doSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="doSearch">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="doSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <el-table :data="list" v-loading="isLoading" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="kbName" label="知识库名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ownerName" label="创建者" width="120" align="center" />
      <el-table-column prop="documentCount" label="文档数" width="80" align="center" />
      <el-table-column prop="createdAt" label="创建时间" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)" v-permission="'KB:UPDATE'">编辑</el-button>
          <el-popconfirm title="确定要删除该知识库吗？" confirm-button-text="确定" cancel-button-text="取消" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button link type="danger" size="small" v-permission="'KB:DELETE'">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
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

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑知识库' : '创建知识库'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="知识库名称" prop="kbName">
          <el-input v-model="form.kbName" placeholder="请输入知识库名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="封面图片URL">
          <el-input v-model="form.coverUrl" placeholder="可选，输入封面图片链接" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { knowledgeBaseApi } from '@/api/modules/knowledgeBase'
import { usePagination } from '@/composables/usePagination'
import { formatDate } from '@/utils/format'
import type { KnowledgeBaseVO, KnowledgeBaseSaveDTO } from '@/api/types/knowledgeBase'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'

const pagination = usePagination()
const query = reactive({ kbName: '', status: undefined as number | undefined })
const list = ref<KnowledgeBaseVO[]>([])
const isLoading = ref(false)
const submitting = ref(false)

// 弹窗相关
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<KnowledgeBaseSaveDTO>({
  kbName: '',
  description: '',
  coverUrl: '',
  status: 1,
})

const rules: FormRules = {
  kbName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }],
}

async function fetchList() {
  isLoading.value = true
  try {
    const res = await knowledgeBaseApi.list({
      ...query,
      page: pagination.params.page,
      size: pagination.params.size,
    })
    list.value = res.records
    pagination.setTotal(res.total)
  } finally {
    isLoading.value = false
  }
}

function doSearch() {
  pagination.reset()
  pagination.params.page = 1
  fetchList()
}

function resetSearch() {
  query.kbName = ''
  query.status = undefined
  doSearch()
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.kbName = ''
  form.description = ''
  form.coverUrl = ''
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: KnowledgeBaseVO) {
  isEdit.value = true
  editId.value = row.id
  form.kbName = row.kbName
  form.description = row.description
  form.coverUrl = row.coverUrl || ''
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await knowledgeBaseApi.update(editId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await knowledgeBaseApi.create({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  await knowledgeBaseApi.delete(id)
  ElMessage.success('删除成功')
  fetchList()
}

watch(pagination.params, () => fetchList(), { immediate: true })
</script>

<style scoped>
.search-bar {
  margin-bottom: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 4px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

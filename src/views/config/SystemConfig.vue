<template>
  <div class="config-page">
    <div class="config-header">
      <h2>系统配置</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon> 添加配置
        </el-button>
      </div>
    </div>

    <div class="config-body">
      <!-- 左侧分类 -->
      <div class="config-sidebar">
        <div
          v-for="cat in categories"
          :key="cat.value"
          :class="['category-item', { active: activeCategory === cat.value }]"
          @click="switchCategory(cat.value)"
        >
          <span class="cat-label">{{ cat.label }}</span>
          <el-tag size="small" type="info">{{ cat.count }}</el-tag>
        </div>
      </div>

      <!-- 右侧表格 -->
      <div class="config-main">
        <el-table :data="list" v-loading="isLoading" stripe border style="width: 100%">
          <el-table-column prop="label" label="参数名" min-width="160">
            <template #default="{ row }">
              <div>
                <div class="config-label-name">{{ row.label || row.configKey }}</div>
                <div class="config-label-key">{{ row.configKey }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="configValue" label="当前值" min-width="160">
            <template #default="{ row }">
              <template v-if="row.configType === 'BOOLEAN'">
                <el-tag :type="row.configValue === 'true' ? 'success' : 'danger'" size="small">{{ row.configValue }}</el-tag>
              </template>
              <span v-else class="config-value">{{ row.configValue }}</span>
              <span v-if="row.defaultVal" class="config-default">默认: {{ row.defaultVal }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="85" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="typeTagType(row.configType)">{{ row.configType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="校验规则" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.validationRule" class="validation-hint">{{ formatRule(row.validationRule) }}</span>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column label="生效策略" width="90" align="center">
            <template #default="{ row }">
              <el-tooltip :content="strategyTooltip(row.reloadStrategy)">
                <el-tag size="small" :type="strategyTagType(row.reloadStrategy)">{{ row.reloadStrategy || 'kafka' }}</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEdit(row)" :disabled="row.editable === 0">编辑</el-button>
              <el-button link type="warning" size="small" @click="openHistory(row)">历史</el-button>
              <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
                <template #reference>
                  <el-button link type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑配置' : '添加配置'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="参数名" prop="label">
          <el-input v-model="form.label" placeholder="如: 默认分块大小" />
        </el-form-item>
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如: chunk.default_size" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="当前值" prop="configValue">
          <template v-if="form.configType === 'BOOLEAN'">
            <el-switch v-model="boolValue" active-text="true" inactive-text="false" @change="onBoolChange" />
          </template>
          <template v-else-if="form.configType === 'NUMBER'">
            <el-input v-model="form.configValue" placeholder="请输入数字" @blur="validateField" />
            <span v-if="fieldError" style="color: #f56c6c; font-size: 12px; margin-left: 8px;">{{ fieldError }}</span>
          </template>
          <template v-else>
            <el-input v-model="form.configValue" placeholder="请输入值" @blur="validateField" />
          </template>
        </el-form-item>
        <el-form-item label="类型" prop="configType">
          <el-select v-model="form.configType" style="width: 100%" @change="onTypeChange">
            <el-option v-for="t in ['STRING', 'NUMBER', 'BOOLEAN', 'JSON']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效策略" prop="reloadStrategy">
          <el-select v-model="form.reloadStrategy" style="width: 100%">
            <el-option label="Kafka推送（即时生效）" value="kafka" />
            <el-option label="API重载（2-5秒）" value="api" />
            <el-option label="需重启服务" value="restart" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="参数说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存并发布</el-button>
      </template>
    </el-dialog>

    <!-- 变更历史弹窗 -->
    <el-dialog v-model="historyVisible" title="变更历史" width="700px" destroy-on-close>
      <el-timeline v-if="historyList.length > 0">
        <el-timeline-item
          v-for="h in historyList"
          :key="h.id"
          :timestamp="h.changedAt"
          placement="top"
        >
          <div class="history-item">
            <p><strong>{{ h.changedByName || '系统' }}</strong> 修改</p>
            <p>
              <span class="history-old">{{ h.oldValue || '(新建)' }}</span>
              <el-icon><ArrowRight /></el-icon>
              <span class="history-new">{{ h.newValue }}</span>
            </p>
            <el-button link type="primary" size="small" @click="handleRollback(h)">回滚到此版本</el-button>
          </div>
        </el-timeline>
      </el-timeline>
      <el-empty v-else description="暂无变更记录" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { Plus, ArrowRight } from '@element-plus/icons-vue'
import { configApi } from '@/api/modules/configs'
import { CONFIG_TYPE_MAP } from '@/utils/constants'
import type { SystemConfigVO, ConfigHistoryVO } from '@/api/types/config'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'

// ─── 分类定义 ───
const categories = ref<{ value: string; label: string; count: number }[]>([])
const activeCategory = ref('')
const CATEGORY_LABELS: Record<string, string> = {
  chunk: '分块策略', retrieval: '检索参数', cleaning: '文档清洗',
  qa: '问答设置', rate_limit: '限流控制', review: '审核策略',
  upload: '文件上传', general: '通用'
}

async function loadCategories() {
  try {
    // 拉每个分类的计数
    const cats: { value: string; label: string; count: number }[] = []
    for (const [val, label] of Object.entries(CATEGORY_LABELS)) {
      const res = await configApi.listByCategory(val)
      if (res && res.length > 0) {
        cats.push({ value: val, label, count: res.length })
      }
    }
    // 按数量排序
    cats.sort((a, b) => b.count - a.count)
    categories.value = cats
    if (cats.length > 0 && !activeCategory.value) {
      activeCategory.value = cats[0].value
    }
  } catch { /* ignore */ }
}

function switchCategory(cat: string) {
  activeCategory.value = cat
  fetchList()
}

// ─── 列表 ───
const list = ref<SystemConfigVO[]>([])
const isLoading = ref(false)

async function fetchList() {
  isLoading.value = true
  try {
    const res = await configApi.listByCategory(activeCategory.value)
    list.value = res || []
  } finally {
    isLoading.value = false
  }
}

watch(activeCategory, () => fetchList(), { immediate: false })

// ─── 表单 ───
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const fieldError = ref('')
const boolValue = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Partial<SystemConfigVO> & { id: number }>({
  id: 0, configKey: '', configValue: '', configType: 'STRING',
  description: '', category: 'general', label: '', reloadStrategy: 'kafka',
})

const formRules: FormRules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }],
  configType: [{ required: true, message: '请选择类型', trigger: 'change' }],
}

// ─── 校验 ───
function parseValidationRule(row: SystemConfigVO): { type: string; min?: number; max?: number; values?: string[]; regex?: string } | null {
  if (!row.validationRule) return null
  try { return JSON.parse(row.validationRule) } catch { return null }
}

function formatRule(ruleJson: string): string {
  const rule = parseValidationRule({ validationRule: ruleJson } as any)
  if (!rule) return '—'
  if (rule.type === 'int' || rule.type === 'float') return `${rule.type} [${rule.min ?? '-∞'}, ${rule.max ?? '+∞'}]`
  if (rule.type === 'enum') return `枚举: ${(rule.values || []).slice(0, 3).join(', ')}`
  if (rule.type === 'bool') return 'true/false'
  if (rule.type === 'pattern') return `正则: ${rule.regex}`
  return rule.type
}

async function validateField() {
  fieldError.value = ''
  if (!form.configValue) return
  try {
    const res = await configApi.validate({
      configKey: form.configKey,
      configValue: form.configValue,
      configType: form.configType,
      validationRule: null as any, // 不传校验规则，靠后端已有规则
    } as any)
    if (res && res.valid === 'false') {
      fieldError.value = res.error
    }
  } catch { /* ignore */ }
}

function onBoolChange(val: boolean) {
  form.configValue = String(val)
}

function onTypeChange() {
  form.configValue = ''
  boolValue.value = false
  fieldError.value = ''
}

// ─── 标签样式 ───
function typeTagType(type: string) { return type === 'NUMBER' ? 'warning' : type === 'BOOLEAN' ? 'success' : type === 'JSON' ? 'danger' : 'info' }
function strategyTagType(s: string) { return s === 'kafka' ? 'success' : s === 'api' ? 'warning' : 'danger' }
function strategyTooltip(s: string) {
  if (s === 'kafka') return 'Kafka推送，下次调用即时生效'
  if (s === 'api') return '组件API热重载，2-5秒生效'
  if (s === 'restart') return '需手动重启对应服务'
  return '未知'
}

// ─── CRUD 操作 ───
function openCreate() {
  isEdit.value = false
  form.id = 0; form.configKey = ''; form.configValue = ''; form.configType = 'STRING'
  form.description = ''; form.category = activeCategory.value || 'general'
  form.label = ''; form.reloadStrategy = 'kafka'
  boolValue.value = false; fieldError.value = ''
  dialogVisible.value = true
}

function openEdit(row: SystemConfigVO) {
  isEdit.value = true
  form.id = row.id; form.configKey = row.configKey; form.configValue = row.configValue
  form.configType = row.configType; form.description = row.description || ''
  form.category = row.category || 'general'; form.label = row.label || ''
  form.reloadStrategy = row.reloadStrategy || 'kafka'
  boolValue.value = row.configValue === 'true'; fieldError.value = ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  // 先执行校验
  await validateField()
  if (fieldError.value) {
    ElMessage.error('参数校验失败: ' + fieldError.value)
    return
  }
  submitting.value = true
  try {
    const res = await configApi.save({
      configKey: form.configKey,
      configValue: form.configValue,
      configType: form.configType,
      description: form.description,
      category: form.category,
      label: form.label,
      reloadStrategy: form.reloadStrategy,
    } as any)
    if (res.validated) {
      ElMessage.success({
        message: `配置已发布！生效策略: ${res.reloadStrategy === 'kafka' ? 'Kafka推送(即时)' : res.reloadStrategy === 'api' ? 'API重载(2-5s)' : '需重启服务'}`,
        type: res.reloadStrategy === 'restart' ? 'warning' : 'success',
        duration: 5000,
      })
    }
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

// ─── 历史与回滚 ───
const historyVisible = ref(false)
const historyList = ref<ConfigHistoryVO[]>([])
const historyKey = ref('')

async function openHistory(row: SystemConfigVO) {
  historyKey.value = row.configKey
  historyVisible.value = true
  historyList.value = []
  try {
    historyList.value = await configApi.getHistory(row.configKey)
  } catch { historyList.value = [] }
}

async function handleRollback(h: ConfigHistoryVO) {
  try {
    await ElMessageBox.confirm(
      `确定回滚 "${h.configKey}" 到历史版本？\n旧值: ${h.oldValue || '(空)'}`,
      '回滚确认', { type: 'warning', confirmButtonText: '确定回滚', cancelButtonText: '取消' }
    )
    const res = await configApi.rollback(h.configKey, h.id)
    ElMessage.success(`已回滚到: ${res.newValue}`)
    historyVisible.value = false
    fetchList()
  } catch { /* cancelled */ }
}

// ─── 初始化 ───
loadCategories().then(() => {
  if (categories.value.length > 0) {
    activeCategory.value = categories.value[0].value
    fetchList()
  }
})
</script>

<style scoped>
.config-page { height: 100%; display: flex; flex-direction: column; }
.config-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.config-header h2 { margin: 0; font-size: 18px; }
.config-body { display: flex; gap: 16px; flex: 1; min-height: 0; }

.config-sidebar { width: 160px; flex-shrink: 0; border-right: 1px solid #e4e7ed; padding-right: 12px; }
.category-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px;
  cursor: pointer; border-radius: 6px; margin-bottom: 4px; transition: all 0.15s; }
.category-item:hover { background: #ecf5ff; }
.category-item.active { background: #d9ecff; color: #409eff; font-weight: 500; }
.cat-label { font-size: 14px; }

.config-main { flex: 1; min-width: 0; }
.config-label-name { font-weight: 500; font-size: 14px; }
.config-label-key { font-size: 12px; color: #909399; font-family: monospace; }
.config-value { font-family: monospace; font-size: 14px; }
.config-default { font-size: 11px; color: #c0c4cc; margin-left: 6px; }
.validation-hint { font-size: 12px; color: #909399; }

.history-item { padding: 4px 0; }
.history-old { color: #f56c6c; text-decoration: line-through; margin-right: 6px; font-family: monospace; }
.history-new { color: #67c23a; margin-left: 6px; font-weight: 500; font-family: monospace; }
</style>

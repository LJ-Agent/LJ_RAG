<template>
  <div class="config-page">
    <div class="config-header">
      <h2>配置管理</h2>
      <el-button v-if="activeScope === 'system'" v-permission="'CONFIG:MANAGE'" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 添加配置
      </el-button>
    </div>

    <!-- 系统/个人 维度切换 -->
    <el-tabs v-model="activeScope" @tab-change="onScopeChange" type="border-card" style="margin-bottom:12px">
      <el-tab-pane name="system"><template #label>🔧 系统配置（仅管理员）</template></el-tab-pane>
      <el-tab-pane name="personal"><template #label>👤 个人偏好</template></el-tab-pane>
    </el-tabs>

    <!-- ========== 系统配置 ========== -->
    <template v-if="activeScope === 'system'">
      <el-tabs v-model="activeCategory" @tab-change="onCategoryChange" type="card">
        <el-tab-pane v-for="c in categories" :key="c.value" :name="c.value">
          <template #label>
            <span>{{ c.label }} <el-tag size="small" type="info" style="margin-left:4px">{{ c.count }}</el-tag></span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <div v-if="activeCategory === 'chunk'" class="strategy-tabs">
        <el-radio-group v-model="chunkSubFilter" size="small">
          <el-radio-button value="default">⭐ 默认策略</el-radio-button>
          <el-radio-button value="common">通用参数</el-radio-button>
          <el-radio-button value="fixed">Fixed</el-radio-button>
          <el-radio-button value="recursive">Recursive</el-radio-button>
          <el-radio-button value="semantic">Semantic</el-radio-button>
          <el-radio-button value="topic">Topic</el-radio-button>
          <el-radio-button value="hybrid">Hybrid</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="filteredList" v-loading="isLoading" stripe border>
        <el-table-column label="参数名" min-width="180">
          <template #default="{ row }">
            <div class="param-cell">
              <div class="param-label">
                <el-icon v-if="row.required===1" class="lock-icon"><Lock /></el-icon>
                {{ row.label || row.configKey }}
              </div>
              <div class="param-key">{{ row.configKey }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="当前值" min-width="140">
          <template #default="{ row }">
            <el-tag v-if="row.configType==='BOOLEAN'" :type="row.configValue==='true'?'success':'danger'" size="small">{{ row.configValue }}</el-tag>
            <span v-else class="config-val">{{ row.configValue }}</span>
            <span v-if="row.defaultVal" class="config-default">默认:{{ row.defaultVal }}</span>
          </template>
        </el-table-column>
        <el-table-column label="校验" width="130" show-overflow-tooltip>
          <template #default="{ row }"><span class="rule-hint">{{ row.validationRule ? formatRule(row.validationRule) : '—' }}</span></template>
        </el-table-column>
        <el-table-column label="作用域" width="75" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.scope==='personal'?'success':''"> {{ row.scope==='personal'?'个人':'系统' }} </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效" width="70" align="center">
          <template #default="{ row }">
            <el-tooltip :content="strategyTooltip(row.reloadStrategy)">
              <el-tag size="small" :type="strategyTagType(row.reloadStrategy)">{{ row.reloadStrategy==='kafka'?'即时':row.reloadStrategy==='api'?'热载':'重启' }}</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="openHistory(row)">历史</el-button>
            <el-tooltip v-if="row.required===1" content="关键配置不可删除"><el-icon class="lock-btn"><Lock /></el-icon></el-tooltip>
            <el-popconfirm v-else title="删除后使用默认值, 确定?" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!isLoading && filteredList.length===0" description="暂无配置" :image-size="60" />
    </template>

    <!-- ========== 个人偏好 ========== -->
    <template v-if="activeScope === 'personal'">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" title="个人偏好配置与您的账号绑定, 优先于系统默认值" />
      <el-table :data="personalList" v-loading="personalLoading" stripe border>
        <el-table-column label="参数" min-width="160">
          <template #default="{ row }">
            <div class="param-label">{{ row.label || row.configKey }}</div>
            <div class="param-key">{{ row.configKey }}</div>
          </template>
        </el-table-column>
        <el-table-column label="当前值" min-width="200">
          <template #default="{ row }">
            <template v-if="row.personalKey">
              <span class="config-val">{{ row.personalValue }}</span>
              <el-tag size="small" type="success" style="margin-left:6px">已自定义</el-tag>
            </template>
            <template v-else>
              <span class="config-val" style="color:#c0c4cc">{{ row.configValue || '未设置' }}</span>
              <el-tag size="small" type="info" style="margin-left:6px">系统默认</el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPersonalEdit(row)">修改</el-button>
            <el-button v-if="row.personalKey" link type="danger" size="small" @click="resetPersonal(row)">恢复默认</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 编辑抽屉 -->
    <el-drawer v-model="drawerVisible" :title="isEdit ? '编辑配置' : '添加配置'" size="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <el-form-item label="参数名"><el-input v-model="form.label" placeholder="中文显示名" /></el-form-item>
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="chunk.default_size" :disabled="isEdit" />
          <div v-if="isEdit" style="font-size:11px;color:#e6a23c;margin-top:2px">键名不可修改</div>
        </el-form-item>
        <el-form-item label="当前值" prop="configValue">
          <el-switch v-if="form.configType==='BOOLEAN'" v-model="boolValue" active-text="true" inactive-text="false" @change="onBoolChange" />
          <el-input-number v-else-if="form.configType==='NUMBER'" v-model="numValue" :min="minFromRule()" :max="maxFromRule()" style="width:100%" @change="onNumChange" />
          <el-input v-else v-model="form.configValue" placeholder="值" @blur="validateField" />
          <div v-if="fieldError" class="field-error">{{ fieldError }}</div>
        </el-form-item>
        <el-form-item label="类型"><el-select v-model="form.configType" style="width:100%" disabled><el-option v-for="t in ['STRING','NUMBER','BOOLEAN','JSON']" :key="t" :label="t" :value="t" /></el-select><div style="font-size:11px;color:#e6a23c;margin-top:2px">类型不可修改(创建时选定)</div></el-form-item>
        <el-form-item label="分类"><el-select v-model="form.category" style="width:100%"><el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" /></el-select></el-form-item>
        <el-form-item label="生效策略"><el-select v-model="form.reloadStrategy" style="width:100%"><el-option label="Kafka即时生效" value="kafka" /><el-option label="API热载(2~5s)" value="api" /><el-option label="需重启服务" value="restart" /></el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="drawerVisible=false">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">保存并发布</el-button></template>
    </el-drawer>

    <!-- 个人偏好编辑弹窗 -->
    <el-dialog v-model="personalEditVisible" title="修改个人偏好" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item :label="personalEditRow.label || personalEditRow.configKey">
          <el-input-number v-if="personalEditRow.configType==='NUMBER'" v-model="personalEditValue" style="width:100%"
            :min="personalRuleMin()" :max="personalRuleMax()" />
          <el-switch v-else-if="personalEditRow.configType==='BOOLEAN'" v-model="personalEditBool" active-text="true" inactive-text="false" />
          <el-select v-else-if="personalEditRow.validationRule && isEnumRule(personalEditRow.validationRule)" v-model="personalEditStr" style="width:100%">
            <el-option v-for="v in enumValues(personalEditRow.validationRule)" :key="v" :label="v" :value="v" />
          </el-select>
          <el-input v-else v-model="personalEditStr" placeholder="请输入值" />
        </el-form-item>
        <div style="font-size:12px;color:#909399;margin-top:4px">{{ personalEditRow.description }}</div>
      </el-form>
      <template #footer><el-button @click="personalEditVisible=false">取消</el-button><el-button type="primary" @click="savePersonal">保存</el-button></template>
    </el-dialog>

    <!-- 变更历史 -->
    <el-drawer v-model="historyVisible" title="变更历史" size="420px" direction="rtl">
      <el-timeline v-if="historyList.length>0">
        <el-timeline-item v-for="h in historyList" :key="h.id" :timestamp="h.changedAt" placement="top">
          <p><strong>{{ h.changedByName||'系统' }}</strong> 修改 <code>{{ historyKey }}</code></p>
          <p><span class="history-old">{{ h.oldValue||'(新建)' }}</span><el-icon><ArrowRight /></el-icon><span class="history-new">{{ h.newValue }}</span></p>
          <el-button link type="primary" size="small" @click="handleRollback(h)">回滚</el-button>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无记录" :image-size="60" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { Plus, ArrowRight, Lock } from '@element-plus/icons-vue'
import { configApi } from '@/api/modules/configs'
import type { SystemConfigVO, ConfigHistoryVO } from '@/api/types/config'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

// ─── 维度: system / personal ───
const activeScope = ref('system')
function onScopeChange() {
  if (activeScope.value === 'system') fetchList()
  else loadPersonalConfigs()
}

// ─── 系统配置: 分类 ───
const CATEGORY_LABELS: Record<string,string> = { chunk:'分块策略',retrieval:'检索参数',cleaning:'文档清洗',qa:'问答设置',rate_limit:'限流控制',review:'审核策略',upload:'文件上传',general:'通用' }
const categories = ref<{value:string;label:string;count:number}[]>([])
const activeCategory = ref('chunk')
const chunkSubFilter = ref('default')

async function loadCategories() {
  const cats: {value:string;label:string;count:number}[] = []
  for (const [val,label] of Object.entries(CATEGORY_LABELS)) {
    try { const res = await configApi.listByCategory(val); if (res?.length) cats.push({value:val,label,count:res.length}) } catch {}
  }
  cats.sort((a,b)=>b.count-a.count); categories.value = cats
  if (cats.length>0 && !activeCategory.value) activeCategory.value = cats[0].value
}
function onCategoryChange() { chunkSubFilter.value='default'; fetchList() }

// ─── 列表 + 分块子筛选 ───
const list = ref<SystemConfigVO[]>([]), isLoading = ref(false)
const STRATEGY_KEY = 'chunk.strategy'
const COMMON_KEYS = ['chunk.default_size','chunk.overlap','chunk.min_chunk_size','chunk.max_chunk_size']
const filteredList = computed(() => {
  if (activeCategory.value !== 'chunk') return list.value
  // ⭐默认策略Tab: 仅展示 chunk.strategy (选择哪种策略作为默认)
  if (chunkSubFilter.value === 'default') return list.value.filter(r => r.configKey === STRATEGY_KEY)
  // 通用参数Tab: default_size/overlap/min_chunk_size/max_chunk_size
  if (chunkSubFilter.value === 'common') return list.value.filter(r => COMMON_KEYS.includes(r.configKey))
  // 策略Tab: 只展示该策略专属参数, 与上传表单一一对应
  return list.value.filter(r => r.configKey.startsWith('chunk.' + chunkSubFilter.value + '.'))
})

async function fetchList() { isLoading.value=true; try { const res=await configApi.listByCategory(activeCategory.value); list.value=res||[] } finally { isLoading.value=false } }

// ─── 个人偏好 ───
const personalList = ref<any[]>([]), personalLoading = ref(false)
async function loadPersonalConfigs() {
  personalLoading.value = true
  try {
    // 获取系统个人模板 + 用户个人配置
    const [templates, userConfigs] = await Promise.all([
      request.get('/configs?scope=personal&page=1&size=20').catch(() => ({records:[]})),
      request.get('/user/configs').catch(() => []),
    ])
    const tmpl = (templates as any).records || []
    const ucfgs = (userConfigs as any) || []
    personalList.value = tmpl.map((t: any) => {
      const u = ucfgs.find((uc:any) => uc.configKey === t.configKey)
      return { ...t, personalKey: u?.configKey || '', personalValue: u?.configValue || '', personalId: u?.id || 0 }
    })
  } finally { personalLoading.value = false }
}

const personalEditVisible = ref(false), personalEditRow = ref<any>({})
const personalEditValue = ref(0), personalEditBool = ref(false), personalEditStr = ref('')
function personalRuleMin() { try { return JSON.parse(personalEditRow.value.validationRule||'{}').min } catch { return undefined } }
function personalRuleMax() { try { return JSON.parse(personalEditRow.value.validationRule||'{}').max } catch { return undefined } }
function isEnumRule(r: string) { try { return JSON.parse(r).type === 'enum' } catch { return false } }
function enumValues(r: string) { try { return JSON.parse(r).values||[] } catch { return [] } }

function openPersonalEdit(row: any) {
  personalEditRow.value = row
  if (row.configType==='NUMBER') personalEditValue.value = Number(row.personalValue || row.configValue) || 0
  else if (row.configType==='BOOLEAN') personalEditBool.value = (row.personalValue || row.configValue) === 'true'
  else personalEditStr.value = row.personalValue || row.configValue || ''
  personalEditVisible.value = true
}

async function savePersonal() {
  let value = ''
  if (personalEditRow.value.configType==='NUMBER') value = String(personalEditValue.value)
  else if (personalEditRow.value.configType==='BOOLEAN') value = String(personalEditBool.value)
  else value = personalEditStr.value
  try {
    await request.post('/user/configs', { configKey: personalEditRow.value.configKey, configValue: value })
    ElMessage.success('个人偏好已保存')
    personalEditVisible.value = false
    loadPersonalConfigs()
  } catch { ElMessage.error('保存失败') }
}

async function resetPersonal(row: any) {
  if (!row.personalId) return
  try { await request.delete(`/user/configs/${row.personalId}`); ElMessage.success('已恢复系统默认'); loadPersonalConfigs() } catch {}
}

// ─── 表单 (系统配置) ───
const drawerVisible = ref(false), isEdit = ref(false), submitting = ref(false)
const fieldError = ref(''), boolValue = ref(false), numValue = ref(0)
const formRef = ref<FormInstance>()
const form = reactive({ id:0, configKey:'', configValue:'', configType:'STRING' as any, description:'', category:'chunk', label:'', reloadStrategy:'kafka', defaultVal:'', validationRule:'' })
const formRules: FormRules = { configKey:[{required:true,message:'请输入配置键',trigger:'blur'}], configValue:[{required:true,message:'请输入配置值',trigger:'blur'}] }

function openCreate() { isEdit.value=false; resetForm(); form.category=activeCategory.value; drawerVisible.value=true }
function openEdit(row: SystemConfigVO) { isEdit.value=true; form.id=row.id; form.configKey=row.configKey; form.configValue=row.configValue; form.configType=row.configType; form.description=row.description||''; form.category=row.category||'chunk'; form.label=row.label||''; form.reloadStrategy=row.reloadStrategy||'kafka'; form.defaultVal=row.defaultVal||''; form.validationRule=row.validationRule||''; boolValue.value=row.configValue==='true'; numValue.value=Number(row.configValue)||0; fieldError.value=''; drawerVisible.value=true }
function resetForm() { form.id=0;form.configKey='';form.configValue='';form.configType='STRING';form.description='';form.category='chunk';form.label='';form.reloadStrategy='kafka';form.defaultVal='';form.validationRule='';boolValue.value=false;numValue.value=0;fieldError.value='' }
function onBoolChange(v:boolean) { form.configValue = String(v) }
function onNumChange(v:number|undefined) { form.configValue = String(v??0) }
function onTypeChange() { form.configValue='';boolValue.value=false;numValue.value=0;fieldError.value='' }
function minFromRule() { try { return JSON.parse(form.validationRule||'{}').min } catch { return undefined } }
function maxFromRule() { try { return JSON.parse(form.validationRule||'{}').max } catch { return undefined } }

async function validateField() { fieldError.value=''; if(!form.configValue) return; try { const r=await configApi.validate({configKey:form.configKey,configValue:form.configValue,configType:form.configType} as any); if(r?.valid==='false') fieldError.value=r.error } catch {} }
function formatRule(r: string) { const rule = (()=>{try{return JSON.parse(r)}catch{return null}})(); if(!rule) return '—'; if(rule.type==='int'||rule.type==='float') return `${rule.type}[${rule.min??'-∞'},${rule.max??'+∞'}]`; if(rule.type==='enum') return `枚举:${(rule.values||[]).slice(0,3).join(',')}`; return rule.type }
function strategyTagType(s:string) { return s==='kafka'?'success':s==='api'?'warning':'danger' }
function strategyTooltip(s:string) { return s==='kafka'?'Kafka推送即时生效':s==='api'?'组件热载2~5秒':s==='restart'?'需手动重启服务':'' }

async function handleSubmit() { const v=await formRef.value?.validate().catch(()=>false); if(!v) return; await validateField(); if(fieldError.value) { ElMessage.error('校验失败:'+fieldError.value); return }; submitting.value=true; try { await configApi.save({configKey:form.configKey,configValue:form.configValue,configType:form.configType,description:form.description,category:form.category,label:form.label,reloadStrategy:form.reloadStrategy} as any); ElMessage.success('已发布'); drawerVisible.value=false; fetchList() } finally { submitting.value=false } }
async function handleDelete(id:number) { await configApi.delete(id); ElMessage.success('已删除'); fetchList() }

// ─── 历史 ───
const historyVisible=ref(false), historyList=ref<ConfigHistoryVO[]>([]), historyKey=ref('')
async function openHistory(row:SystemConfigVO) { historyKey.value=row.configKey; historyVisible.value=true; historyList.value=[]; try { historyList.value=await configApi.getHistory(row.configKey) } catch { historyList.value=[] } }
async function handleRollback(h:ConfigHistoryVO) { try { await ElMessageBox.confirm(`回滚"${h.configKey}"到旧值:${h.oldValue||'(空)'}?`,'回滚确认',{type:'warning'}); await configApi.rollback(h.configKey,h.id); ElMessage.success('已回滚'); historyVisible.value=false; fetchList() } catch {} }

// ─── 初始化 ───
loadCategories().then(()=>{ if(categories.value.length>0) { activeCategory.value=categories.value[0].value; fetchList() } })
</script>

<style scoped>
.config-page{height:100%;display:flex;flex-direction:column}
.config-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
.config-header h2{margin:0;font-size:18px}
.strategy-tabs{padding:8px 12px;background:#f5f7fa;border-radius:6px;margin-bottom:12px;display:flex;align-items:center}
.param-cell{line-height:1.4}
.param-label{font-weight:500;font-size:14px;display:flex;align-items:center;gap:2px}
.param-key{font-size:12px;color:#909399;font-family:monospace}
.config-val{font-family:monospace;font-size:14px}
.config-default{font-size:11px;color:#c0c4cc;margin-left:6px}
.rule-hint{font-size:12px;color:#909399}
.lock-icon{color:#e6a23c;font-size:14px;flex-shrink:0}
.lock-btn{color:#c0c4cc;font-size:16px;cursor:not-allowed}
.field-error{color:#f56c6c;font-size:12px;margin-top:4px}
.history-old{color:#f56c6c;text-decoration:line-through;margin-right:8px;font-family:monospace}
.history-new{color:#67c23a;margin-left:8px;font-weight:500;font-family:monospace}
</style>

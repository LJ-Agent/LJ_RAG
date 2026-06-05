<template>
  <div class="config-page">
    <div class="config-header">
      <h2>配置管理</h2>
      <el-button v-if="activeScope==='system'" v-permission="'CONFIG:MANAGE'" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 添加
      </el-button>
    </div>


    <!-- ========== 系统配置 ========== -->
    <template v-if="activeScope==='system'">
      <div class="config-body">
        <div class="config-sidebar">
          <div v-for="c in sysCats" :key="c.value"
            :class="['category-item',{active:activeCat===c.value}]"
            @click="activeCat=c.value; loadSysList()">
            <span class="cat-label">{{ c.label }}</span>
            <el-tag size="small" type="info">{{ c.count }}</el-tag>
          </div>
        </div>
        <div class="config-main">
          <el-table :data="sysList" v-loading="sysLoading" stripe border>
        <el-table-column label="参数名" min-width="160">
          <template #default="{row}">
            <div class="param-label">{{ row.label||row.configKey }}</div>
            <div class="param-key">{{ row.configKey }}</div>
          </template>
        </el-table-column>
        <el-table-column label="当前值" min-width="140">
          <template #default="{row}">
            <el-tag v-if="row.configType==='BOOLEAN'" :type="row.configValue==='true'?'success':'danger'" size="small">{{ row.configValue }}</el-tag>
            <span v-else class="config-val">{{ row.configValue }}</span>
          </template>
        </el-table-column>
        <el-table-column label="校验" width="120" show-overflow-tooltip>
          <template #default="{row}"><span class="rule-hint">{{ row.validationRule ? fmtRule(row.validationRule) : '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-tooltip v-if="row.required===1" content="关键配置不可删"><el-icon class="lock-btn"><Lock /></el-icon></el-tooltip>
            <el-popconfirm v-else title="确定删除?" @confirm="handleDelete(row.id)"><template #reference><el-button link type="danger" size="small">删除</el-button></template></el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
        </div>
      </div>
    </template>

    <!-- ========== 个人偏好 ========== -->
    <template v-if="activeScope==='personal'">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" title="个人偏好与您的账号绑定, 优先于系统默认值。文件上传/问答时将使用您的偏好作为参数。" />
      <div class="config-body">
        <!-- 左侧分类 -->
        <div class="config-sidebar">
          <div v-for="c in personalCats" :key="c.value"
            :class="['category-item',{active:activeCat===c.value}]"
            @click="activeCat=c.value">
            <span class="cat-label">{{ c.label }}</span>
            <el-tag size="small" type="info">{{ c.count }}</el-tag>
          </div>
        </div>
        <!-- 右侧表格 -->
        <div class="config-main">
          <!-- 分块策略子筛选 -->
          <div v-if="activeCat==='chunk'" class="strategy-tabs">
            <el-radio-group v-model="chunkSub" size="small">
              <el-radio-button value="default">⭐ 默认策略</el-radio-button>
              <el-radio-button value="common">通用参数</el-radio-button>
              <el-radio-button value="fixed">Fixed</el-radio-button>
              <el-radio-button value="hierarchical">Hierarchical</el-radio-button>
              <el-radio-button value="recursive">Recursive</el-radio-button>
              <el-radio-button value="semantic">Semantic</el-radio-button>
              <el-radio-button value="topic">Topic</el-radio-button>
              <el-radio-button value="hybrid">Hybrid</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="filteredPersonal" v-loading="personalLoading" stripe border>
            <el-table-column label="参数名" min-width="160">
              <template #default="{row}">
                <div class="param-label">{{ row.label||row.configKey }}</div>
                <div class="param-key">{{ row.configKey }}</div>
              </template>
            </el-table-column>
            <el-table-column label="当前值" min-width="180">
              <template #default="{row}">
                <span class="config-val" :style="{color:row.fromUser?'#303133':'#c0c4cc'}">{{ row.configValue||'未设置' }}</span>
                <el-tag v-if="row.fromUser" size="small" type="success" style="margin-left:6px">已自定义</el-tag>
                <el-tag v-else size="small" type="info" style="margin-left:6px">系统默认</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="校验" width="120" show-overflow-tooltip>
              <template #default="{row}"><span class="rule-hint">{{ row.validationRule ? fmtRule(row.validationRule) : '—' }}</span></template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="100" align="center">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openPersonalEdit(row)">修改</el-button>
                <el-button v-if="row.fromUser" link type="danger" size="small" @click="resetPersonal(row)">恢复</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </template>

    <!-- 编辑抽屉(系统配置) -->
    <el-drawer v-model="drawerVisible" :title="isEdit?'编辑配置':'添加配置'" size="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <el-form-item label="参数名"><el-input v-model="form.label" placeholder="中文显示名"/></el-form-item>
        <el-form-item label="配置键" prop="configKey"><el-input v-model="form.configKey" :disabled="isEdit"/><div v-if="isEdit" style="font-size:11px;color:#e6a23c">键名不可修改</div></el-form-item>
        <el-form-item label="当前值" prop="configValue">
          <el-switch v-if="form.configType==='BOOLEAN'" v-model="boolVal" active-text="true" inactive-text="false" @change="v=>form.configValue=String(v)"/>
          <el-select v-else-if="isEnum(form.validationRule)" v-model="form.configValue" style="width:100%"><el-option v-for="v in evals(form.validationRule)" :key="v" :label="v" :value="v"/></el-select>
          <el-input-number v-else-if="form.configType==='NUMBER'" v-model="numVal" :min="rMin()" :max="rMax()" style="width:100%" @change="v=>form.configValue=String(v??0)"/>
          <el-input v-else v-model="form.configValue" @blur="doValidate"/>
          <div v-if="fieldError" class="field-error">{{ fieldError }}</div>
        </el-form-item>
        <el-form-item label="类型"><el-select v-model="form.configType" style="width:100%" disabled><el-option v-for="t in ['STRING','NUMBER','BOOLEAN','JSON']" :key="t" :label="t" :value="t"/></el-select><div style="font-size:11px;color:#e6a23c">类型不可修改</div></el-form-item>
        <el-form-item label="分类"><el-select v-model="form.category" style="width:100%"><el-option v-for="c in sysCats" :key="c.value" :label="c.label" :value="c.value"/></el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="drawerVisible=false">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button></template>
    </el-drawer>

    <!-- 个人偏好编辑弹窗 -->
    <el-dialog v-model="pEditVisible" title="修改个人偏好" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item :label="pRow.label||pRow.configKey">
          <el-input-number v-if="pRow.configType==='NUMBER'" v-model="pNumVal" style="width:100%" :min="pMin()" :max="pMax()"/>
          <el-switch v-else-if="pRow.configType==='BOOLEAN'" v-model="pBoolVal" active-text="true" inactive-text="false"/>
          <el-select v-else-if="isEnum(pRow.validationRule)" v-model="pStrVal" style="width:100%"><el-option v-for="v in evals(pRow.validationRule)" :key="v" :label="v" :value="v"/></el-select>
          <el-input v-else v-model="pStrVal"/>
        </el-form-item>
        <div style="font-size:12px;color:#909399">{{ pRow.description }}<br/>系统默认: {{ pRow.defaultValue }}</div>
      </el-form>
      <template #footer><el-button @click="pEditVisible=false">取消</el-button><el-button type="primary" @click="savePersonal">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { Plus, Lock } from '@element-plus/icons-vue'
import { configApi } from '@/api/modules/configs'
import type { SystemConfigVO } from '@/api/types/config'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

// ─── 维度 ───
const activeScope = ref('system')
const activeCat = ref('chunk')

// ─── 系统配置 ───
const SYS_CATS: Record<string,string> = { upload:'文件上传', rate_limit:'限流控制' }
const sysCats = ref<{value:string;label:string;count:number}[]>([])
const sysList = ref<SystemConfigVO[]>([])
const sysLoading = ref(false)

async function loadSysCats() {
  const cats: {value:string;label:string;count:number}[] = []
  for (const [v,l] of Object.entries(SYS_CATS)) {
    try { const r = await configApi.listByCategory(v); if (r?.length) cats.push({value:v,label:l,count:r.length}) } catch {}
  }
  sysCats.value = cats; if (cats.length && !activeCat.value) activeCat.value = cats[0].value
}
async function loadSysList() { sysLoading.value=true; try { const r=await configApi.listByCategory(activeCat.value); sysList.value=r||[] } finally { sysLoading.value=false } }
function onCatChange() { if (activeScope.value==='system') loadSysList(); /* personal uses computed */ }

// ─── 个人配置 ───
const PERSONAL_CATS: Record<string,string> = { chunk:'分块策略', retrieval:'检索参数', cleaning:'文档清洗', review:'审核策略', qa:'问答设置', personal:'个人默认' }
const personalCats = ref<{value:string;label:string;count:number}[]>([])
const personalAll = ref<any[]>([])
const personalLoading = ref(false)
const chunkSub = ref('default')

// 分块子筛选
const STRATEGY_KEY = 'chunk.strategy', COMMON_KEYS = ['chunk.default_size','chunk.overlap','chunk.min_chunk_size','chunk.max_chunk_size']
const filteredPersonal = computed(() => {
  if (activeCat.value !== 'chunk') return personalAll.value.filter((r:any) => r.category === activeCat.value)
  if (chunkSub.value === 'default') return personalAll.value.filter((r:any) => r.configKey === STRATEGY_KEY)
  if (chunkSub.value === 'common') return personalAll.value.filter((r:any) => COMMON_KEYS.includes(r.configKey))
  return personalAll.value.filter((r:any) => r.configKey.startsWith('chunk.'+chunkSub.value+'.'))
})

async function loadPersonalConfigs() {
  personalLoading.value = true
  try {
    const res = await request.post('/user/configs/effective')
    personalAll.value = (res as any[]) || []
    // 构建分类
    const cats: Record<string,number> = {}
    for (const r of personalAll.value) { const c = r.category||'general'; cats[c] = (cats[c]||0) + 1 }
    personalCats.value = Object.entries(PERSONAL_CATS).filter(([k]) => cats[k]).map(([v,l]) => ({value:v,label:l,count:cats[v]||0}))
    if (personalCats.value.length && activeCat.value==='chunk' && !cats['chunk']) activeCat.value = personalCats.value[0]?.value || 'chunk'
  } finally { personalLoading.value = false }
}

// ─── 个人偏好编辑 ───
const pEditVisible = ref(false), pRow = ref<any>({}), pNumVal = ref(0), pBoolVal = ref(false), pStrVal = ref('')
function pMin() { try { return JSON.parse(pRow.value.validationRule||'{}').min } catch { return undefined } }
function pMax() { try { return JSON.parse(pRow.value.validationRule||'{}').max } catch { return undefined } }
function openPersonalEdit(row: any) {
  pRow.value = row
  if (row.configType==='NUMBER') pNumVal.value = Number(row.configValue)||0
  else if (row.configType==='BOOLEAN') pBoolVal.value = row.configValue==='true'
  else pStrVal.value = row.configValue||''
  pEditVisible.value = true
}
async function savePersonal() {
  let val = ''
  if (pRow.value.configType==='NUMBER') val = String(pNumVal.value)
  else if (pRow.value.configType==='BOOLEAN') val = String(pBoolVal.value)
  else val = pStrVal.value
  try { await request.post('/user/configs',{configKey:pRow.value.configKey,configValue:val}); ElMessage.success('已保存'); pEditVisible.value=false; loadPersonalConfigs() } catch { ElMessage.error('保存失败') }
}
async function resetPersonal(row: any) { if (!row.userConfigId) return; try { await request.delete(`/user/configs/${row.userConfigId}`); ElMessage.success('已恢复默认'); loadPersonalConfigs() } catch {} }

// ─── 系统编辑 ───
const drawerVisible = ref(false), isEdit = ref(false), submitting = ref(false), fieldError = ref('')
const boolVal = ref(false), numVal = ref(0), formRef = ref<FormInstance>()
const form = reactive({ id:0,configKey:'',configValue:'',configType:'STRING',description:'',category:'upload',label:'',validationRule:'' })
const formRules: FormRules = { configKey:[{required:true,message:'请输入键'}], configValue:[{required:true,message:'请输入值'}] }
function openCreate() { isEdit.value=false; form.id=0;form.configKey='';form.configValue='';form.configType='STRING';form.description='';form.category=activeCat.value;form.label='';form.validationRule=''; boolVal.value=false;numVal.value=0;fieldError.value=''; drawerVisible.value=true }
function openEdit(row: any) { isEdit.value=true; form.id=row.id;form.configKey=row.configKey;form.configValue=row.configValue;form.configType=row.configType;form.description=row.description||'';form.category=row.category||'upload';form.label=row.label||'';form.validationRule=row.validationRule||''; boolVal.value=row.configValue==='true';numVal.value=Number(row.configValue)||0;fieldError.value=''; drawerVisible.value=true }
async function doValidate() { fieldError.value=''; if(!form.configValue) return; try { const r=await configApi.validate({configKey:form.configKey,configValue:form.configValue,configType:form.configType} as any); if(r?.valid==='false') fieldError.value=r.error } catch {} }
async function handleSubmit() { const v=await formRef.value?.validate().catch(()=>false); if(!v) return; await doValidate(); if(fieldError.value) { ElMessage.error('校验失败:'+fieldError.value); return } submitting.value=true; try { await configApi.save({configKey:form.configKey,configValue:form.configValue,configType:form.configType,description:form.description,category:form.category,label:form.label} as any); ElMessage.success('已保存'); drawerVisible.value=false; loadSysList() } finally { submitting.value=false } }
async function handleDelete(id:number) { await configApi.delete(id); ElMessage.success('已删除'); loadSysList() }

// ─── 共用工具 ───
function isEnum(r: string) { try { return JSON.parse(r).type==='enum' } catch { return false } }
function evals(r: string) { try { return JSON.parse(r).values||[] } catch { return [] } }
function rMin() { try { return JSON.parse(form.validationRule||'{}').min } catch { return undefined } }
function rMax() { try { return JSON.parse(form.validationRule||'{}').max } catch { return undefined } }
function fmtRule(r: string) { const rule=(()=>{try{return JSON.parse(r)}catch{return null}})(); if(!rule) return '—'; if(rule.type==='int'||rule.type==='float') return `${rule.type}[${rule.min??'-∞'},${rule.max??'+∞'}]`; if(rule.type==='enum') return `枚举`; return rule.type }

function onScopeChange() {
  if (activeScope.value==='system') { loadSysCats().then(()=>{ if(sysCats.value.length){ activeCat.value=sysCats.value[0].value; loadSysList() } }) }
  else { activeCat.value='chunk'; loadPersonalConfigs() }
}

// 初始化: 根据当前 scope 加载对应数据
onScopeChange()
</script>

<style scoped>
.config-page{height:100%;display:flex;flex-direction:column}
.config-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
.config-header h2{margin:0;font-size:18px}
.config-body{display:flex;gap:16px;flex:1;min-height:0}
.config-sidebar{width:160px;flex-shrink:0;border-right:1px solid #e4e7ed;padding-right:12px}
.category-item{display:flex;justify-content:space-between;align-items:center;padding:10px 12px;cursor:pointer;border-radius:6px;margin-bottom:4px;transition:all .15s}
.category-item:hover{background:#ecf5ff}
.category-item.active{background:#d9ecff;color:#409eff;font-weight:500}
.cat-label{font-size:14px}
.config-main{flex:1;min-width:0}
.strategy-tabs{padding:8px 12px;background:#f5f7fa;border-radius:6px;margin-bottom:12px}
.param-label{font-weight:500;font-size:14px}
.param-key{font-size:12px;color:#909399;font-family:monospace}
.config-val{font-family:monospace;font-size:14px}
.rule-hint{font-size:12px;color:#909399}
.lock-btn{color:#c0c4cc;font-size:16px;cursor:not-allowed}
.field-error{color:#f56c6c;font-size:12px;margin-top:4px}
</style>

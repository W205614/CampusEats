<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { useSession } from '../stores/session';
import type { components } from '../../../contracts/openapi';

type User = Required<components['schemas']['LocalUserView']>;
type Page = Omit<Required<components['schemas']['LocalUsersPage']>, 'records' | 'nextCursor'> & {records: User[]; nextCursor: string | null};
const session = useSession();
const rows = ref<User[]>([]), query = ref(''), enabled = ref('ALL'), loading = ref(false), more = ref(false), busy = ref(false);
const dialog = ref(false), mode = ref<'create' | 'edit' | 'reset'>('create'), selected = ref<User | null>(null);
const form = ref({ username: '', name: '', password: '', enabled: true });
let cursor: string | null = null, sequence = 0, active = true, appliedQuery = '', appliedEnabled = 'ALL';
async function load(reset = true) {
  if (!reset && (loading.value || !more.value)) return;
  const current = ++sequence, token = session.session?.token;
  const targetQuery = reset ? query.value : appliedQuery, targetEnabled = reset ? enabled.value : appliedEnabled;
  const params = new URLSearchParams({ limit: '50', query: targetQuery });
  if (targetEnabled !== 'ALL') params.set('enabled', targetEnabled);
  if (!reset && cursor) params.set('cursor', cursor);
  loading.value = true;
  try {
    const page = await request<Page>('/platform/users/page?' + params);
    if (!active || current !== sequence || token !== session.session?.token) return;
    const merged = new Map((reset ? [] : rows.value).map(row => [row.id, row]));
    page.records.forEach(row => merged.set(row.id, row));
    rows.value = [...merged.values()]; cursor = page.nextCursor; more.value = page.hasMore;
    appliedQuery = targetQuery; appliedEnabled = targetEnabled;
  } catch (error) { if (active && current === sequence && token === session.session?.token) notifyError(error); }
  finally { if (active && current === sequence && token === session.session?.token) loading.value = false; }
}
function open(kind: typeof mode.value, row?: User) {
  mode.value = kind; selected.value = row || null;
  form.value = { username: row?.username || '', name: row?.name || '', password: '', enabled: row?.enabled ?? true };
  dialog.value = true;
}
function clearPassword() { form.value.password = ''; }
async function save() {
  if (busy.value) return;
  if (mode.value !== 'edit' && (form.value.password.length < 12 || form.value.password.length > 128)) {
    notifyError(new Error('密码须为12至128个字符')); return;
  }
  busy.value = true;
  try {
    if (mode.value === 'create') await request<User>('/platform/users', 'POST', {
      username: form.value.username, name: form.value.name, password: form.value.password,
    });
    else if (mode.value === 'reset') await request<User>('/platform/users/' + selected.value!.id + '/reset-password', 'POST', { password: form.value.password });
    else await request<User>('/platform/users/' + selected.value!.id, 'PUT', { name: form.value.name, enabled: form.value.enabled });
    clearPassword(); dialog.value = false; await load();
  } catch (error) { notifyError(error); }
  finally { busy.value = false; }
}
watch(() => session.session?.token, () => {
  ++sequence; rows.value = []; cursor = null; more.value = false; loading.value = false; dialog.value = false; clearPassword();
});
onMounted(() => { void load(); });
onUnmounted(() => { active = false; ++sequence; clearPassword(); });
</script>
<template>
  <section class="page-heading"><div><h1>独立测试用户</h1><p class="muted">由平台创建账号，使用测试资料；支付与退款仍为模拟。</p></div><el-button type="primary" @click="open('create')">创建独立测试用户</el-button></section>
  <section class="panel">
    <el-form inline @submit.prevent="load()"><el-form-item label="账号或名称"><el-input v-model="query" maxlength="80" placeholder="查询账号或显示名称" /></el-form-item><el-form-item label="账号状态"><el-select v-model="enabled" style="width:140px"><el-option label="全部" value="ALL"/><el-option label="启用" value="true"/><el-option label="停用" value="false"/></el-select></el-form-item><el-button :loading="loading" native-type="submit">查询用户</el-button></el-form>
    <el-table :data="rows" row-key="id"><el-table-column prop="username" label="账号"/><el-table-column prop="name" label="显示名称"/><el-table-column label="状态"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }} · {{ row.mustChangePassword ? '待首次改密' : '已改密' }}</template></el-table-column><el-table-column prop="createdAt" label="创建时间"/><el-table-column label="操作" width="220"><template #default="{ row }"><el-button text @click="open('edit',row)">编辑用户</el-button><el-button text @click="open('reset',row)">重置密码</el-button></template></el-table-column></el-table>
    <p v-if="!loading && !rows.length" class="muted">没有符合条件的独立测试用户。</p><el-button v-if="more" :loading="loading" @click="load(false)">加载更多用户</el-button>
  </section>
  <el-dialog v-model="dialog" :title="mode === 'create' ? '创建独立测试用户' : mode === 'reset' ? '重置账号密码' : '修改独立测试用户'" width="480px" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @closed="clearPassword">
    <el-form label-position="top" @submit.prevent="save">
      <el-form-item v-if="mode === 'create'" label="独立账号"><el-input v-model="form.username" maxlength="32" autocomplete="off" placeholder="3至32位字母、数字、点、横线或下划线" /></el-form-item>
      <p v-else class="muted">账号：{{ selected?.username }}</p>
      <el-form-item v-if="mode !== 'reset'" label="显示名称"><el-input v-model="form.name" maxlength="32" placeholder="请使用测试名称"/></el-form-item>
      <el-form-item v-if="mode !== 'edit'" :label="mode === 'reset' ? '重置密码' : '初始密码'"><el-input v-model="form.password" type="password" autocomplete="new-password" maxlength="128" placeholder="12至128个字符"/></el-form-item>
      <el-form-item v-if="mode === 'edit'" label="启用账号"><el-switch v-model="form.enabled"/></el-form-item>
      <p class="muted">{{ mode === 'edit' ? '保存变更将撤销该用户的旧会话。' : '用户首次登录必须改密；重置后旧会话立即失效。请通过安全方式交付密码。' }}</p>
      <el-button type="primary" native-type="submit" :loading="busy">{{ mode === 'reset' ? '确认重置' : '保存用户' }}</el-button>
    </el-form>
  </el-dialog>
</template>

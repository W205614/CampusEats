<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { selectedStore } from '../context';
import { useStorePages } from '../store-pages';
import { useSession } from '../stores/session';
import { usePagedList } from '../paged-list';
import { roles } from '../../../contracts/models';
import { readPages } from '../../../contracts/paging';
import { ApiError } from '../../../contracts/api-error';
import {
  cents,
  states,
  type Campus,
  type Store,
  type Staff,
  type Aftersale,
  type Refund,
  type OutboxEvent,
  type Task,
  type HistoryFilters,
  historyQuery, validateHistoryFilters, aftersaleStates, taskStates,
  type Review,
} from '../../../contracts/platform';
const route = useRoute(),
  router = useRouter(),
  session = useSession(),
  kind = String(route.params.kind),
  platform = computed(() => session.session?.role === 'PLATFORM_ADMIN'),
  campuses = ref<Campus[]>([]),
  campus = ref(''),
  buildings = ref<{ id: string; name: string; enabled: boolean }[]>([]),
  aftersales = ref<Aftersale[]>([]),
  refunds = ref<Refund[]>([]),
  events = ref<OutboxEvent[]>([]),
  reviews = ref<Review[]>([]),
  audit = ref<{ id: string; actorId: string; action: string; detail: string; createdAt: string }[]>(
    [],
  ),
  dialog = ref(false),
  editing = ref(''),
  buildingName = ref(''),
  decision = ref(''),
  reply = ref(''),
  imageUrl = ref('');
const { stores, storeHasMore, storeLoading, loadStores } = useStorePages();
async function moreStores() {
  try { await loadStores(); } catch (e) { notifyError(e); }
}
const history = ['aftersales', 'refunds', 'outbox'].includes(kind);
const paged = history || kind === 'reviews' || kind === 'audit';
type RecordFilters = HistoryFilters & { actorId?: string; action?: string };
const emptyFilters = (): RecordFilters => ({ state: '', orderNumber: '', begin: '', end: '',
  ...(kind === 'audit' ? { actorId: '', action: '' } : kind === 'aftersales' ? {} : { taskId: '' }) });
const filters = ref(emptyFilters()), applied = ref(emptyFilters()), loading = ref(false), busy = ref(false), hasMore = ref(false);
const tasks = computed(() => kind === 'outbox' ? events.value : refunds.value);
let loadedPages = 1, sequence = 0, mounted = false;
let timer: ReturnType<typeof setInterval> | undefined;
watch([() => session.session?.token, selectedStore], () => {
  sequence++;
  loadedPages = 1;
  filters.value = emptyFilters();
  applied.value = emptyFilters();
  aftersales.value = []; refunds.value = []; events.value = []; reviews.value = []; audit.value = [];
  hasMore.value = false;
  loading.value = false;
  busy.value = false;
  decision.value = '';
  handoverOpen.value=false; handoverRequired.value=false;
  form.value.password='';
  if (mounted) void load();
}, { flush: 'sync' });
const form = ref({
  name: '',
  username: '',
  role: 'OPERATOR',
  storeId: selectedStore.value,
  campusId: '',
  enabled: true,
  password: '',
});
const titles: Record<string, string> = {
  staff: '员工与角色',
  buildings: '校区楼栋',
  aftersales: '售后与平台介入',
  refunds: '退款任务',
  outbox: '通知任务',
  reviews: '订单评价',
  audit: '操作记录',
};
const base = () => '/merchant/stores/' + selectedStore.value;
type DispatchStaff = Staff & { acceptingTasks?: boolean };
type ActiveDelivery = { id: string; number: string; status: number; version: string; storeId: string; campusId: string };
const staffQuery = ref(''), staffState = ref(''), appliedStaffQuery = ref(''), appliedStaffState = ref('');
const staffPage = usePagedList<DispatchStaff>(() => (platform.value ? '/platform/staff' : base() + '/staff') + '/page?'
  + new URLSearchParams({ query: appliedStaffQuery.value, ...(appliedStaffState.value ? { state: appliedStaffState.value } : {}) }), row => row.id);
const staff = staffPage.records, staffMore = staffPage.hasMore, staffLoading = staffPage.loading;
const handoverOpen = ref(false), handoverStaff = ref(''), handoverName = ref(''), handoverRequired = ref(false);
const deliveryPage = usePagedList<ActiveDelivery>(() => '/platform/staff/' + handoverStaff.value + '/active-deliveries/page', row => row.id);
const activeDeliveries = deliveryPage.records, deliveryMore = deliveryPage.hasMore, deliveryLoading = deliveryPage.loading;
async function searchStaff() { appliedStaffQuery.value=staffQuery.value; appliedStaffState.value=staffState.value; await staffPage.load(); }
async function openHandover(id: string, name: string) {
  handoverStaff.value=id; handoverName.value=name; handoverOpen.value=true; await deliveryPage.load();
}
function visitDelivery(row: ActiveDelivery) {
  handoverOpen.value=false; dialog.value=false; selectedStore.value=row.storeId;
  void router.push({path:'/orders',query:{orderId:row.id}});
}
async function dispatchAvailability(row: DispatchStaff) {
  if (busy.value) return; busy.value=true;
  try {
    await request('/platform/staff/' + row.id + '/dispatch-availability','PUT',{enabled:row.acceptingTasks === false});
    await staffPage.load();
  } catch(error) {notifyError(error);}
  finally {busy.value=false;}
}
async function emergencyDisable(row: DispatchStaff) {
  if (busy.value) return;
  const token = session.session?.token;
  let reason: string;
  try {
    const result = await ElMessageBox.prompt('将立即撤销该配送员的会话并禁止新分配。在途任务保留并进入异常队列，请随后逐笔交接。','紧急停用配送员',{
      inputType:'textarea',inputPlaceholder:'填写紧急停用原因',confirmButtonText:'确认紧急停用',cancelButtonText:'取消',
      inputValidator: (value: string) => !!value?.trim() && value.trim().length<=200 || '请填写1至200字的原因',
    });
    reason=result.value.trim();
  } catch {return;}
  if(token !== session.session?.token) return;
  busy.value=true;
  try {
    await request('/platform/staff/' + row.id + '/emergency-disable','POST',{reason});
    await staffPage.load(); await openHandover(row.id,row.name);
  } catch(error) {notifyError(error);}
  finally {busy.value=false;}
}
async function load(pages = loadedPages) {
  if (!mounted || !session.session || loading.value || busy.value) return;
  const current = ++sequence;
  loading.value = true;
  try {
    if (kind === 'staff') await staffPage.load();
    if (kind === 'buildings' && campus.value)
      buildings.value = await request<typeof buildings.value>(
        '/platform/campuses/' + campus.value + '/buildings',
      );
    if (history) {
      const endpoint = kind === 'outbox' ? 'events' : kind;
      const path = (platform.value ? '/platform' : base()) + '/' + endpoint;
      const selectedFilters = { ...applied.value };
      const result = await readPages((before, limit) => request<(Aftersale | Refund | OutboxEvent)[]>(path + historyQuery(selectedFilters, before, limit)), pages, () => mounted && current === sequence);
      if (current !== sequence) return;
      if (kind === 'aftersales') aftersales.value = result.items as Aftersale[];
      else if (kind === 'outbox') events.value = result.items as OutboxEvent[];
      else refunds.value = result.items as Refund[];
      hasMore.value = result.hasMore;
      loadedPages = pages;
    }
    if (kind === 'reviews') {
      const path = base() + '/reviews';
      const result = await readPages(async (beforeId, limit) =>
        (await request<{ records: Review[] }>(path + historyQuery(emptyFilters(), beforeId, limit))).records,
        pages, () => mounted && current === sequence);
      if (current !== sequence) return;
      reviews.value = result.items; hasMore.value = result.hasMore; loadedPages = pages;
    }
    if (kind === 'audit') {
      const path = platform.value ? '/platform/audit' : base() + '/audit';
      const selectedFilters = { ...applied.value };
      const result = await readPages((beforeId, limit) =>
        request<typeof audit.value>(path + historyQuery(selectedFilters, beforeId, limit)),
        pages, () => mounted && current === sequence);
      if (current !== sequence) return;
      audit.value = result.items; hasMore.value = result.hasMore; loadedPages = pages;
    }
  } catch (e) {
    if (current === sequence) notifyError(e);
  } finally {
    if (current === sequence) loading.value = false;
  }
}
async function search(clear = false) {
  if (busy.value) return;
  try {
    if (clear) filters.value = emptyFilters();
    validateHistoryFilters(filters.value);
    if (kind === 'audit') {
      if (filters.value.actorId && !/^[1-9]\d*$/.test(filters.value.actorId.trim()))
        throw new Error('操作人编号须为正整数');
      if (filters.value.action && !/^[A-Z][A-Z0-9_]{0,47}$/.test(filters.value.action.trim()))
        throw new Error('操作类型须为完整的操作代码');
    }
    applied.value = { ...filters.value };
    sequence++;
    loading.value = false;
    loadedPages = 1;
    aftersales.value = []; refunds.value = []; events.value = []; reviews.value = []; audit.value = [];
    hasMore.value = false;
    await load();
  } catch (e) { notifyError(e); }
}
function edit(s?: Staff) {
  handoverRequired.value=false;
  editing.value = s?.id || '';
  form.value = {
    name: s?.name || '',
    username: s?.username || '',
    role: s?.role || 'OPERATOR',
    storeId: s?.storeId || selectedStore.value,
    campusId: s?.campusId || campus.value,
    enabled: s ? s.status === 1 : true,
    password: '',
  };
  dialog.value = true;
  if (platform.value && form.value.storeId && !stores.value.some(row => row.id === form.value.storeId))
    void (async () => {
      try { while (mounted && dialog.value && storeHasMore.value && !stores.value.some(row => row.id === form.value.storeId)) {
        if (!await loadStores()) break;
      } } catch (e) { if (mounted) notifyError(e); }
    })();
}
async function saveStaff() {
  if(busy.value) return;
  busy.value=true;
  try {
    const f = form.value;
    await request(
      (platform.value ? '/platform/staff' : '/merchant/staff') +
        (editing.value ? '/' + editing.value : ''),
      editing.value ? 'PUT' : 'POST',
      {
        ...f,
        password: f.password || null,
        storeId: ['SHOP_MANAGER', 'OPERATOR'].includes(f.role) ? f.storeId : null,
        campusId: f.role === 'DELIVERER' ? f.campusId : null,
      },
    );
    dialog.value = false;
    form.value.password = '';
    busy.value=false;
    await load();
  } catch (e) {
    if(e instanceof ApiError && e.code === 'COURIER_HANDOVER_REQUIRED') handoverRequired.value=true;
    notifyError(e);
  } finally {busy.value=false;}
}
async function addBuilding() {
  try {
    await request('/platform/campuses/' + campus.value + '/buildings', 'POST', {
      name: buildingName.value,
      enabled: true,
    });
    buildingName.value = '';
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function toggleBuilding(b: (typeof buildings.value)[number]) {
  try {
    await request('/platform/campuses/' + campus.value + '/buildings/' + b.id, 'PUT', {
      name: b.name,
      enabled: !b.enabled,
    });
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function renameBuilding(b: (typeof buildings.value)[number]) {
  if(busy.value) return;
  const token=session.session?.token, target=campus.value;
  let name: string;
  try {
    const result=await ElMessageBox.prompt('历史订单继续显示下单时的楼栋快照。','修改楼栋名称',{
      inputValue:b.name,inputPlaceholder:'楼栋新名称',confirmButtonText:'保存楼栋名称',cancelButtonText:'取消',
      inputValidator:(value:string)=>!!value?.trim() && value.trim().length<=80 || '请填写1至80字的名称',
    }); name=result.value.trim();
  } catch {return;}
  if(token!==session.session?.token || target!==campus.value) return;
  busy.value=true;
  try {
    await request('/platform/campuses/'+target+'/buildings/'+b.id,'PUT',{name,enabled:b.enabled});
    busy.value=false; await load();
  } catch(error) {notifyError(error);} finally {busy.value=false;}
}
async function mutateHistory(operation: () => Promise<void>) {
  if (!mounted || loading.value || busy.value) return;
  const current = sequence;
  busy.value = true;
  try {
    await operation();
    if (mounted && current === sequence) {
      busy.value = false;
      await load();
    }
  } catch (e) {
    if (mounted && current === sequence) notifyError(e);
  } finally {
    if (mounted && current === sequence) busy.value = false;
  }
}
async function decide(r: Aftersale, approve: boolean) {
  const current = sequence;
  await mutateHistory(async () => {
    if (!decision.value.trim()) throw new Error('请先填写处理说明');
    await request(
      (platform.value && r.state === 'ESCALATED' ? '/platform' : '/merchant') +
        '/aftersales/' + r.id + '/decision',
      'POST', { version: r.version, approve, reason: decision.value },
    );
    if (mounted && current === sequence) decision.value = '';
  });
}
async function retry(r: Task) {
  await mutateHistory(() => request(
    '/merchant/' + (kind === 'outbox' ? 'events' : 'refunds') + '/' + r.id + '/retry', 'POST',
  ));
}
async function replyReview(r: Review) {
  try {
    await request('/merchant/reviews/' + r.id + '/reply', 'POST', { text: reply.value });
    reply.value = '';
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function hide(r: Review) {
  try {
    if (!decision.value.trim()) throw new Error('请填写隐藏原因');
    await request('/platform/reviews/' + r.id + '/hide', 'POST', { text: decision.value });
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function viewEvidence(id: string) {
  try {
    const response = await fetch('/api/v1/evidence/' + id, {
      headers: { Authorization: 'Bearer ' + session.session?.token },
    });
    if (!response.ok) {
      const result = await response.json().catch(() => null);
      const fallback = response.status === 429 || response.status === 503
        ? '举证读取繁忙，请稍后重试' : '无法读取该图片，请确认权限或稍后重试';
      throw new Error(typeof result?.message === 'string' && result.message ? result.message : fallback);
    }
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value);
    imageUrl.value = URL.createObjectURL(await response.blob());
  } catch (e) {
    notifyError(e);
  }
}
onMounted(async () => {
  mounted = true;
  if (platform.value) {
    try {
      const [campusRows] = await Promise.all([request<Campus[]>('/platform/campuses'), loadStores(true)]);
      if (!mounted) return;
      campuses.value = campusRows;
    } catch (e) { if (mounted) notifyError(e); }
    if (!mounted) return;
    campus.value = campuses.value[0]?.id || '';
  }
  await load();
  if (mounted && history) timer = setInterval(() => void load(), 15000);
});
onUnmounted(() => {
  mounted = false;
  form.value.password = '';
  sequence++;
  if (timer) clearInterval(timer);
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value);
});
</script>
<template>
  <section class="page-head">
    <div>
      <h1>{{ titles[kind] }}</h1>
      <p class="muted">按当前身份与店铺权限展示，重要操作留有审计记录。</p>
    </div>
    <el-button :loading="loading" @click="load()">刷新</el-button>
  </section>
  <section class="panel" v-if="history || kind === 'audit'" aria-label="历史记录筛选">
    <el-form inline @submit.prevent="search()">
      <el-form-item v-if="history" label="状态"><el-select v-model="filters.state" placeholder="全部状态" clearable style="width: 150px"><el-option v-for="s in kind === 'aftersales' ? aftersaleStates : taskStates" :key="s" :value="s" :label="states[s] || s" /></el-select></el-form-item>
      <el-form-item v-if="history" label="订单号"><el-input v-model="filters.orderNumber" placeholder="完整订单号" maxlength="64" /></el-form-item>
      <el-form-item v-if="history && kind !== 'aftersales'" label="任务编号"><el-input v-model="filters.taskId" placeholder="完整任务编号" maxlength="19" /></el-form-item>
      <el-form-item v-if="kind === 'audit'" label="操作人编号"><el-input v-model="filters.actorId" placeholder="完整操作人编号" maxlength="19" /></el-form-item>
      <el-form-item v-if="kind === 'audit'" label="操作类型"><el-input v-model="filters.action" placeholder="例如 STORE_SAVE" maxlength="48" /></el-form-item>
      <el-form-item label="上海日期"><el-date-picker v-model="filters.begin" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" /><el-date-picker v-model="filters.end" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit" :disabled="loading">筛选</el-button><el-button :disabled="loading" @click="search(true)">清空筛选</el-button></el-form-item>
    </el-form>
    <p class="muted">每页最多100条，日期包含首尾（上海时间），最多366天。刷新保留已加载历史。</p>
  </section>
  <section class="panel" v-if="kind === 'staff'">
    <el-form inline @submit.prevent="searchStaff"><el-form-item label="账号或姓名"><el-input v-model="staffQuery" maxlength="80"/></el-form-item><el-form-item label="员工状态"><el-select v-model="staffState" style="width:140px"><el-option value="" label="全部"/><el-option value="ENABLED" label="启用"/><el-option value="DISABLED" label="停用"/></el-select></el-form-item><el-button native-type="submit" :loading="staffLoading">查询员工</el-button></el-form>
    <el-button type="primary" @click="edit()">新增员工</el-button
    ><el-table :data="staff"
      ><el-table-column prop="username" label="账户" /><el-table-column
        prop="name"
        label="姓名"
      /><el-table-column label="角色"
        ><template #default="{ row }">{{ roles[row.role] }}</template></el-table-column
      ><el-table-column label="状态"
        ><template #default="{ row }">{{
          row.status === 1 ? '启用' : '停用'
        }}</template></el-table-column
      ><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button text @click="edit(row)">编辑 / 重置密码</el-button><template v-if="platform && row.role === 'DELIVERER'"><el-button text :disabled="busy || row.status !== 1" @click="dispatchAvailability(row)">{{ row.acceptingTasks === false ? '开启接新单' : '停止接新单' }}</el-button><el-button text @click="openHandover(row.id,row.name)">未完成任务</el-button><el-button text type="danger" :disabled="busy || row.status !== 1" @click="emergencyDisable(row)">紧急停用</el-button></template></template
        ></el-table-column
      ></el-table
    >
  </section>
  <section v-if="kind === 'staff' && staffMore" class="panel"><el-button :loading="staffLoading" @click="staffPage.load(false)">加载更多员工</el-button></section>
  <section class="panel" v-if="kind === 'buildings'">
    <div class="toolbar">
      <el-select v-model="campus" @change="load"
        ><el-option v-for="c in campuses" :key="c.id" :value="c.id" :label="c.name" /></el-select
      ><el-input v-model="buildingName" placeholder="新增楼栋名称" style="width: 200px" /><el-button
        :disabled="!buildingName"
        @click="addBuilding"
        >新增楼栋</el-button
      >
    </div>
    <el-table :data="buildings"
      ><el-table-column prop="name" label="楼栋" /><el-table-column label="配送"
        ><template #default="{ row }">{{
          row.enabled ? '启用' : '停用'
        }}</template></el-table-column
      ><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button text :disabled="busy" @click="renameBuilding(row)">修改名称</el-button><el-button text @click="toggleBuilding(row)">{{
            row.enabled ? '停用' : '启用'
          }}</el-button></template
        ></el-table-column
      ></el-table
    >
  </section>
  <section class="panel" v-if="kind === 'aftersales'">
    <p>店铺处理申请；被拒绝或超过48小时未处理的申请可由用户申请平台介入。</p>
    <el-input
      v-model="decision"
      placeholder="本次处理说明"
      maxlength="200"
      style="margin-bottom: 20px"
    />
    <article v-for="a in aftersales" :key="a.id" class="panel">
      <div class="toolbar">
        <strong>申请 {{ a.id }} · 订单 {{ a.orderId }}</strong
        ><el-tag>{{ states[a.state] || a.state }}</el-tag
        ><strong>¥{{ cents(a.amountCents) }}</strong>
      </div>
      <p>{{ a.reason }}</p>
      <p v-if="a.decision">处理说明：{{ a.decision }}</p>
      <p v-for="i in a.items" :key="i.detailId">{{ i.name }} × {{ i.quantity }}</p>
      <div class="toolbar">
        <el-button v-for="(e, index) in a.evidence" :key="e.id" @click="viewEvidence(e.id)"
          >查看举证 {{ index + 1 }}</el-button
        >
      </div>
      <div class="toolbar" v-if="a.state === 'PENDING' || (platform && a.state === 'ESCALATED')">
        <el-button type="primary" @click="decide(a, true)">同意退款 / 取消</el-button
        ><el-button @click="decide(a, false)">拒绝并说明</el-button>
      </div>
    </article>
    <p v-if="!aftersales.length" class="muted">暂无售后申请。</p>
  </section>
  <section class="panel" v-if="kind === 'refunds' || kind === 'outbox'">
    <el-table :data="tasks"
      ><el-table-column prop="id" :label="kind === 'outbox' ? '通知编号' : '退款编号'" /><el-table-column
        :prop="kind === 'outbox' ? 'aggregateId' : 'orderId'"
        label="订单"
      /><el-table-column v-if="kind === 'outbox'" prop="eventType" label="通知类型" /><el-table-column v-else label="金额"
        ><template #default="{ row }">¥{{ cents(row.amountCents) }}</template></el-table-column
      ><el-table-column label="状态"
        ><template #default="{ row }">{{ states[row.state] || row.state }}</template></el-table-column
      ><el-table-column prop="attempts" label="失败次数" /><el-table-column
        prop="lastError"
        label="原因"
      /><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button v-if="row.state === 'FAILED'" text @click="retry(row)"
            >恢复重试</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </section>
  <section v-if="paged" class="toolbar">
    <el-button v-if="hasMore" :loading="loading" @click="load(loadedPages + 1)">加载更早记录</el-button>
    <span v-else-if="!loading" class="muted">已展示全部匹配记录</span>
  </section>
  <section class="panel" v-if="kind === 'reviews'">
    <div class="toolbar">
      <el-input v-model="reply" placeholder="商家回复内容" maxlength="1000" /><el-input
        v-if="platform"
        v-model="decision"
        placeholder="平台隐藏原因"
        maxlength="200"
      />
    </div>
    <article class="panel" v-for="r in reviews" :key="r.id">
      <strong>{{ r.rating }} 星 · 订单 {{ r.orderId }}</strong>
      <p>{{ r.content }}</p>
      <p v-if="r.reply">商家回复：{{ r.reply }}</p>
      <p v-if="r.hidden">平台已隐藏：{{ r.hideReason }}</p>
      <el-button v-if="!r.reply" :disabled="!reply" @click="replyReview(r)">回复评价</el-button
      ><el-button v-if="platform && !r.hidden" @click="hide(r)">隐藏违规评价</el-button>
    </article>
  </section>
  <section class="panel" v-if="kind === 'audit'">
    <el-table :data="audit"
      ><el-table-column prop="createdAt" label="时间" min-width="180" /><el-table-column
        prop="actorId"
        label="操作人" /><el-table-column prop="action" label="操作" /><el-table-column
        prop="detail"
        label="说明"
    /></el-table>
  </section>
  <el-dialog v-model="dialog" title="员工与权限" width="520px" @closed="form.password = ''"
    ><el-form label-position="top"
      ><el-form-item label="账户"><el-input v-model="form.username" maxlength="32" /></el-form-item
      ><el-form-item label="姓名"><el-input v-model="form.name" maxlength="32" /></el-form-item
      ><el-form-item label="角色"
        ><el-select v-model="form.role"
          ><el-option
            v-for="role in platform
              ? ['PLATFORM_ADMIN', 'SHOP_MANAGER', 'OPERATOR', 'DELIVERER']
              : ['OPERATOR']"
            :key="role"
            :value="role"
            :label="roles[role]" /></el-select></el-form-item
      ><el-form-item
        v-if="platform && ['SHOP_MANAGER', 'OPERATOR'].includes(form.role)"
        label="归属店铺"
        ><el-select v-model="form.storeId"
          ><el-option
            v-for="s in stores"
            :key="s.id"
            :value="s.id"
            :label="s.campusName + ' / ' + s.name" /></el-select><el-button v-if="storeHasMore" :loading="storeLoading" @click="moreStores">加载更多归属店铺</el-button></el-form-item
      ><el-form-item v-if="form.role === 'DELIVERER'" label="配送校区"
        ><el-select v-model="form.campusId"
          ><el-option
            v-for="c in campuses"
            :key="c.id"
            :value="c.id"
            :label="c.name" /></el-select></el-form-item
      ><el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item
      ><el-form-item :label="editing ? '重置密码，留空不修改' : '初始密码，至少12个字符'"
        ><el-input v-model="form.password" type="password" show-password /></el-form-item></el-form
    ><p v-if="handoverRequired" class="muted">该配送员仍有未结束任务。请先完成交接，再保存人员变更；本次表单已保留。</p><el-button v-if="handoverRequired" @click="openHandover(editing,form.name)">查看未完成任务</el-button><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="busy" @click="saveStaff">保存</el-button></template
    ></el-dialog
  ><el-dialog :model-value="!!imageUrl" title="售后举证" @close="imageUrl = ''"
    ><img :src="imageUrl" style="max-width: 100%"
  /></el-dialog>
  <el-dialog v-model="handoverOpen" :title="'未完成配送任务 · ' + handoverName" width="720px" @closed="deliveryPage.cancel">
    <p class="muted">逐笔打开订单，使用平台改派完成交接。不会直接取消或退款。</p>
    <el-table :data="activeDeliveries" row-key="id"><el-table-column prop="number" label="订单号"/><el-table-column label="阶段"><template #default="{row}">{{ row.status === 4 ? '配送中' : '待取餐' }}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><el-button text @click="visitDelivery(row)">查看订单并交接</el-button></template></el-table-column></el-table>
    <p v-if="!deliveryLoading && !activeDeliveries.length" class="muted">当前没有未完成配送任务。</p>
    <el-button v-if="deliveryMore" :loading="deliveryLoading" @click="deliveryPage.load(false)">加载更多配送任务</el-button>
  </el-dialog>
</template>

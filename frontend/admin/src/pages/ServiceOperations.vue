<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { selectedStore } from '../context';
import { useSession } from '../stores/session';
import { usePagedList } from '../paged-list';
interface Policy {
  acceptMinutes: number; prepareMinutes: number; dispatchMinutes: number;
  pickupMinutes: number; deliveryMinutes: number; selfPickupMinutes: number; version: string | number;
}
interface ExceptionView {
  id: string; orderId: string; orderNumber: string; storeId: string; campusId: string;
  kind: string; stage: string; state: string; reason?: string; version: string;
  createdAt: string; deadlineAt?: string | null; migrated: boolean;
}
const session = useSession(), router = useRouter();
const canManage = computed(() => ['PLATFORM_ADMIN', 'SHOP_MANAGER'].includes(session.session?.role || ''));
const state = ref('OPEN'), busy = ref(false), policyLoading = ref(false), policyLoaded = ref(false);
const policy = ref<Policy>({ acceptMinutes: 10, prepareMinutes: 30, dispatchMinutes: 15, pickupMinutes: 15,
  deliveryMinutes: 60, selfPickupMinutes: 120, version: 0 });
const fields: { key: keyof Omit<Policy, 'version'>; label: string }[] = [
  { key: 'acceptMinutes', label: '接单时限' }, { key: 'prepareMinutes', label: '备餐时限' },
  { key: 'dispatchMinutes', label: '待分配时限' }, { key: 'pickupMinutes', label: '待取餐时限' },
  { key: 'deliveryMinutes', label: '配送时限' }, { key: 'selfPickupMinutes', label: '自取时限' },
];
const stages: Record<string, string> = { WAIT_ACCEPT: '待接单', PREPARING: '备餐中', WAIT_DISPATCH: '待分配',
  WAIT_PICKUP: '配送员待取餐', DELIVERING: '配送中', SELF_PICKUP: '等待自取', FINISHED: '已结束' };
const status: Record<string, string> = { OPEN: '待处理', ACKNOWLEDGED: '已确认处理', RESOLVED: '已解除' };
const selected = ref<ExceptionView>(), reason = ref('');
let live = true, policySequence = 0;
const list = usePagedList<ExceptionView>(() => '/merchant/stores/' + selectedStore.value + '/order-exceptions/page'
  + (state.value ? '?state=' + state.value : ''), row => row.id);
const rows = list.records, loading = list.loading, hasMore = list.hasMore;
onBeforeUnmount(() => { live = false; policySequence++; });
async function loadPolicy() {
  if (!canManage.value || !selectedStore.value) return;
  const current = ++policySequence, token = session.session?.token, store = selectedStore.value;
  policyLoading.value = true;
  try {
    const saved = await request<Policy>('/merchant/stores/' + store + '/service-policy');
    if (!live || current !== policySequence || token !== session.session?.token || store !== selectedStore.value) return;
    policy.value = saved; policyLoaded.value = true;
  } catch (e) { if (live && current === policySequence) notifyError(e); }
  finally { if (live && current === policySequence) policyLoading.value = false; }
}
async function savePolicy() {
  if (busy.value || !policyLoaded.value || policyLoading.value) return;
  if (fields.some(field => !Number.isInteger(policy.value[field.key]) || policy.value[field.key] < 1 || policy.value[field.key] > 1440)) {
    notifyError(new Error('每项时限须为1至1440分钟的整数')); return;
  }
  busy.value = true; const store = selectedStore.value, token = session.session?.token;
  try {
    await request('/merchant/stores/' + store + '/service-policy', 'PUT', { ...policy.value });
    if (live && store === selectedStore.value && token === session.session?.token) await loadPolicy();
  } catch (e) { if (live && store === selectedStore.value && token === session.session?.token) notifyError(e); }
  finally { if (live && token === session.session?.token) busy.value = false; }
}
function acknowledge(row: ExceptionView) { selected.value = row; reason.value = ''; }
async function confirm() {
  if (!selected.value || !reason.value.trim() || busy.value) return;
  busy.value = true; const token = session.session?.token, store = selectedStore.value;
  try {
    await request('/order-exceptions/' + selected.value.id + '/acknowledge', 'POST', {
      version: selected.value.version, reason: reason.value.trim(),
    });
    if (!live || token !== session.session?.token || store !== selectedStore.value) return;
    selected.value = undefined; await list.load();
  } catch (e) { if (live && token === session.session?.token && store === selectedStore.value) notifyError(e); }
  finally { if (live && token === session.session?.token) busy.value = false; }
}
async function refresh() { await Promise.all([loadPolicy(), list.load()]); }
watch(state, () => { selected.value = undefined; void list.load(); });
watch(() => session.session?.token, () => {
  policySequence++; policyLoaded.value = false; selected.value = undefined;
  if (session.session) void refresh();
});
onMounted(refresh);
</script>
<template>
  <section class="page-head"><div><h1>履约异常与服务时限</h1><p class="muted">超时用于提醒与人工处置，系统不会因此自动取消订单或退款。后台约每10秒检查，刷新可查看最新结果。</p></div><el-button :loading="loading || policyLoading" :disabled="busy" @click="refresh">刷新</el-button></section>
  <section v-if="canManage" class="panel"><h2>当前店铺服务时限</h2><p class="muted">单位为分钟，允许1至1440。保存仅影响之后开始的阶段，在途阶段继续使用原快照。</p><el-form label-position="top" class="policy-grid" @submit.prevent="savePolicy"><el-form-item v-for="field in fields" :key="field.key" :label="field.label"><el-input-number v-model="policy[field.key]" :min="1" :max="1440" :precision="0" :disabled="!policyLoaded || busy || policyLoading" /></el-form-item><el-form-item><el-button type="primary" native-type="submit" :loading="busy" :disabled="!policyLoaded || policyLoading">保存服务时限</el-button></el-form-item></el-form></section>
  <section class="panel"><div class="toolbar"><h2>异常订单</h2><el-select v-model="state" aria-label="异常状态" style="width:180px"><el-option value="OPEN" label="待处理" /><el-option value="ACKNOWLEDGED" label="已确认处理" /><el-option value="RESOLVED" label="已解除" /><el-option value="" label="全部状态" /></el-select></div><el-table :data="rows"><el-table-column prop="orderNumber" label="订单号" min-width="170" /><el-table-column label="异常"><template #default="{row}">{{ row.kind === 'COURIER_UNAVAILABLE' ? '配送员不可用' : '阶段超时' }}</template></el-table-column><el-table-column label="阶段"><template #default="{row}">{{ stages[row.stage] || row.stage }}</template></el-table-column><el-table-column prop="deadlineAt" label="截止时间" min-width="180" /><el-table-column label="状态"><template #default="{row}">{{ status[row.state] || row.state }}</template></el-table-column><el-table-column prop="reason" label="处理说明" /><el-table-column label="操作" min-width="220"><template #default="{row}"><el-button v-if="row.state !== 'RESOLVED'" :disabled="busy || loading" @click="acknowledge(row)">确认处理</el-button><el-button :disabled="busy" @click="router.push({path:'/orders',query:{orderId:row.orderId}})">前往订单履约</el-button><span v-if="row.migrated" class="muted">迁移基线</span></template></el-table-column></el-table><el-button v-if="hasMore" :loading="loading" :disabled="busy" @click="list.load(false)">加载更多异常</el-button><p class="muted">确认处理只记录人工跟进；订单状态推进或配送人员恢复后，已不成立的异常由系统解除。</p></section>
  <el-dialog :model-value="Boolean(selected)" title="确认跟进异常" @close="selected = undefined"><p v-if="selected">订单 {{ selected.orderNumber }} · {{ stages[selected.stage] || selected.stage }}</p><el-form @submit.prevent="confirm"><el-form-item label="跟进说明"><el-input v-model="reason" type="textarea" maxlength="200" show-word-limit placeholder="说明已联系的人员和下一步处理安排" /></el-form-item><el-button type="primary" native-type="submit" :loading="busy" :disabled="!reason.trim()">确认处理</el-button></el-form></el-dialog>
</template>
<style scoped>.policy-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px}</style>

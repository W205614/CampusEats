<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { selectedStore } from '../context';
import { useSession } from '../stores/session';
import { localDate } from '../dates';
import { cents, type CursorPage } from '../../../contracts/platform';
import type { components } from '../../../contracts/openapi';
type Ledger = components['schemas']['FinanceLedgerLine'];
type Preview = components['schemas']['FinancePreview'];
type Settlement = components['schemas']['FinanceSettlement'];
type Reconciliation = components['schemas']['FinanceReconciliation'];
const session = useSession();
const platform = computed(() => session.session?.role === 'PLATFORM_ADMIN');
const begin = ref(localDate(new Date(Date.now() - 6 * 86400000))), end = ref(localDate());
const businessDate = ref(localDate(new Date(Date.now() - 86400000)));
const preview = ref<Preview>(), ledger = ref<Ledger[]>([]), statements = ref<Settlement[]>([]);
const reconciliation = ref<Reconciliation>();
const ledgerCursor = ref<string | null>(null), statementCursor = ref<string | null>(null);
const appliedQuery = ref('');
const ledgerMore = ref(false), statementMore = ref(false), loading = ref(false), busy = ref(false);
const detail = ref<Settlement>(), entries = ref<Ledger[]>([]), entryCursor = ref<string | null>(null), entryMore = ref(false);
let sequence = 0, live = true;
onBeforeUnmount(() => { live = false; sequence++; });
const prefix = () => '/merchant/stores/' + selectedStore.value;
const dates = () => new URLSearchParams({ begin: begin.value, end: end.value });
const canGenerate = computed(() => Boolean(preview.value && businessDate.value
  && businessDate.value >= preview.value.enabledAt.slice(0, 10)
  && businessDate.value < localDate()));
function merge<T extends { id: string }>(old: T[], fresh: T[]) {
  const byId = new Map(old.map(row => [row.id, row]));
  for (const row of fresh) byId.set(row.id, row);
  return [...byId.values()];
}
async function load() {
  if (!selectedStore.value || busy.value) return;
  const current = ++sequence, token = session.session?.token;
  loading.value = true;
  const base = prefix(), query = dates().toString();
  try {
    const [p, l, s] = await Promise.all([
      request<Preview>(base + '/settlements/preview'),
      request<CursorPage<Ledger>>(base + '/ledger/page?' + query),
      request<CursorPage<Settlement>>(base + '/settlements/page?' + query),
    ]);
    if (!live || current !== sequence || token !== session.session?.token) return;
    preview.value = p; ledger.value = l.records; statements.value = s.records;
    ledgerCursor.value = l.nextCursor; ledgerMore.value = l.hasMore;
    statementCursor.value = s.nextCursor; statementMore.value = s.hasMore;
    appliedQuery.value = query;
    reconciliation.value = undefined; detail.value = undefined;
  } catch (e) { if (live && current === sequence) notifyError(e); }
  finally { if (live && current === sequence) loading.value = false; }
}
async function more(kind: 'ledger' | 'settlements') {
  if (busy.value || loading.value) return;
  const current = sequence, token = session.session?.token, query = new URLSearchParams(appliedQuery.value);
  const cursor = kind === 'ledger' ? ledgerCursor.value : statementCursor.value;
  if (!cursor) return;
  query.set('cursor', cursor); busy.value = true;
  try {
    if (kind === 'ledger') {
      const page = await request<CursorPage<Ledger>>(prefix() + '/ledger/page?' + query);
      if (!live || current !== sequence || token !== session.session?.token) return;
      ledger.value = merge(ledger.value, page.records); ledgerCursor.value = page.nextCursor; ledgerMore.value = page.hasMore;
    } else {
      const page = await request<CursorPage<Settlement>>(prefix() + '/settlements/page?' + query);
      if (!live || current !== sequence || token !== session.session?.token) return;
      statements.value = merge(statements.value, page.records); statementCursor.value = page.nextCursor; statementMore.value = page.hasMore;
    }
  } catch (e) { if (live && current === sequence) notifyError(e); }
  finally { if (live && current === sequence) busy.value = false; }
}
async function generate() {
  if (busy.value || !canGenerate.value) return;
  busy.value = true; const current = sequence;
  try {
    const saved = await request<Settlement>('/platform/settlements', 'POST', {
      storeId: selectedStore.value, businessDate: businessDate.value,
    });
    if (!live || current !== sequence) return;
    statements.value = merge([saved], statements.value);
  } catch (e) { if (live && current === sequence) notifyError(e); }
  finally { if (live && current === sequence) busy.value = false; }
}
async function reconcile() {
  if (busy.value || loading.value) return;
  busy.value = true; const current = sequence, query = dates(); query.set('storeId', selectedStore.value);
  try {
    const result = await request<Reconciliation>('/platform/finance/reconciliation?' + query);
    if (live && current === sequence) reconciliation.value = result;
  } catch (e) { if (live && current === sequence) notifyError(e); }
  finally { if (live && current === sequence) busy.value = false; }
}
async function show(statement: Settlement, reset = true) {
  if (busy.value || loading.value) return;
  const current = sequence, query = new URLSearchParams();
  if (!reset && entryCursor.value) query.set('cursor', entryCursor.value);
  busy.value = true;
  try {
    const page = await request<CursorPage<Ledger>>(prefix() + '/settlements/' + statement.id + '/entries/page?' + query);
    if (!live || current !== sequence) return;
    detail.value = statement; entries.value = reset ? page.records : merge(entries.value, page.records);
    entryCursor.value = page.nextCursor; entryMore.value = page.hasMore;
  } catch (e) { if (live && current === sequence) notifyError(e); }
  finally { if (live && current === sequence) busy.value = false; }
}
onMounted(load);
</script>
<template>
  <section class="page-head"><div><h1>模拟账务</h1><p class="muted">当前店铺的模拟收支、冻结结算与对账。所有金额均不发生真实资金划转。</p></div><el-button :loading="loading" @click="load">刷新</el-button></section>
  <section class="panel"><div class="toolbar"><label>开始日期 <input v-model="begin" type="date" /></label><span>至</span><input v-model="end" type="date" aria-label="结束日期" /><el-button :disabled="busy" @click="load">查询流水与结算</el-button><el-button v-if="platform" :disabled="loading" :loading="busy" @click="reconcile">运行对账</el-button></div><p class="muted">日期筛选控制发生期收支；对账同时核对当前全部订单余额，不自动修改账目。</p></section>
  <section v-if="preview" class="panel" data-testid="finance-preview"><h2>当日模拟结算预览 · {{ preview.businessDate }}</h2><div class="metrics"><div class="metric"><span>期初净额（独立展示）</span><strong>¥{{ cents(preview.openingNetCents) }}</strong></div><div class="metric"><span>今日支付</span><strong>¥{{ cents(preview.paidCents) }}</strong></div><div class="metric"><span>今日退款</span><strong>¥{{ cents(preview.refundedCents) }}</strong></div><div class="metric"><span>今日净额</span><strong>¥{{ cents(preview.netCents) }}</strong></div></div><p class="muted">佣金与渠道手续费为零。{{ preview.carryForward ? '当前净额为负，记为待抵扣。' : '' }}{{ preview.partialDay ? '启用首日从迁移时刻起算，历史余额不伪装为当日流水。' : '' }}</p><p class="muted">快照：{{ preview.generatedAt }}</p></section>
  <section v-if="platform" class="panel"><h2>生成已结束日的模拟结算单</h2><div class="toolbar"><input v-model="businessDate" type="date" aria-label="结算日期" :min="preview?.enabledAt.slice(0,10)" :max="localDate(new Date(Date.now()-86400000))" /><el-button type="primary" :disabled="!canGenerate || loading" :loading="busy" @click="generate">生成模拟结算单</el-button></div><p class="muted">同店同日重复生成返回原单；退款在实际成功日记账，不修改已冻结账单。启用当日请先查看预览，次日才能生成首张日结单。</p></section>
  <section v-if="reconciliation" class="panel" data-testid="finance-reconciliation"><h2>对账结果</h2><p>{{ reconciliation.differenceCount === '0' ? '账目一致' : '存在差异，请人工核对' }} · 差异订单 {{ reconciliation.differenceCount }} 笔</p><p>当前订单净额 ¥{{ cents(reconciliation.currentOrderNetCents) }} / 期初及流水净额 ¥{{ cents(reconciliation.ledgerNetCents) }}</p><p class="muted">{{ reconciliation.begin }} 至 {{ reconciliation.end }}：支付 ¥{{ cents(reconciliation.periodPaidCents) }}，退款 ¥{{ cents(reconciliation.periodRefundedCents) }}。{{ reconciliation.generatedAt }}</p><el-table v-if="reconciliation.differences.length" :data="reconciliation.differences"><el-table-column prop="orderId" label="订单 ID" /><el-table-column prop="orderPaidCents" label="订单支付 / 分" /><el-table-column prop="ledgerPaidCents" label="流水支付 / 分" /><el-table-column prop="orderRefundedCents" label="订单退款 / 分" /><el-table-column prop="ledgerRefundedCents" label="流水退款 / 分" /></el-table><p v-if="reconciliation.hasMoreDifferences" class="muted">当前展示前200笔差异，差异总数以汇总为准。</p></section>
  <section class="panel"><h2>收支流水</h2><el-table :data="ledger"><el-table-column prop="id" label="流水 ID" /><el-table-column label="类型"><template #default="{row}">{{ row.sourceType === 'PAYMENT' ? '模拟支付' : '模拟退款' }}</template></el-table-column><el-table-column prop="orderId" label="订单 ID" /><el-table-column label="金额"><template #default="{row}">¥{{ cents(row.amountCents) }}</template></el-table-column><el-table-column prop="occurredAt" label="发生时间" /></el-table><el-button v-if="ledgerMore" :loading="busy" @click="more('ledger')">加载更多流水</el-button></section>
  <section class="panel"><h2>已冻结模拟结算单</h2><el-table :data="statements"><el-table-column prop="id" label="结算单 ID" /><el-table-column prop="businessDate" label="日期" /><el-table-column label="支付"><template #default="{row}">¥{{ cents(row.paidCents) }}</template></el-table-column><el-table-column label="退款"><template #default="{row}">¥{{ cents(row.refundedCents) }}</template></el-table-column><el-table-column label="净额"><template #default="{row}">¥{{ cents(row.netCents) }}{{ row.carryForward ? '（待抵扣）' : '' }}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><el-button :disabled="busy" @click="show(row)">查看冻结流水</el-button></template></el-table-column></el-table><el-button v-if="statementMore" :loading="busy" @click="more('settlements')">加载更多结算单</el-button></section>
  <el-dialog :model-value="Boolean(detail)" title="冻结结算流水" width="800px" @close="detail = undefined"><p v-if="detail">{{ detail.businessDate }} · {{ detail.periodStart }} 至 {{ detail.periodEnd }} · {{ detail.entryCount }} 条</p><el-table :data="entries"><el-table-column prop="id" label="流水 ID" /><el-table-column prop="orderId" label="订单 ID" /><el-table-column prop="sourceType" label="类型" /><el-table-column label="金额"><template #default="{row}">¥{{ cents(row.amountCents) }}</template></el-table-column></el-table><el-button v-if="entryMore && detail" :loading="busy" @click="show(detail, false)">加载更多冻结流水</el-button></el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { request, exportReport } from '../platform-api';
import { notifyError } from '../api';
import { useSession } from '../stores/session';
import { selectedStore } from '../context';
import { localDate } from '../dates';
import { cents, type Campus } from '../../../contracts/platform';
interface Report {
  generatedAt?: string;
  metrics: Record<string, string | number>;
  daily: { date: string; paidCents: string; paymentCount: number }[];
  stores: { storeName: string; paidCents: string; childOrderCount: number }[];
}
const session = useSession(),
  begin = ref(localDate(new Date(Date.now() - 6 * 86400000))),
  end = ref(localDate()),
  level = ref('STORE'),
  campus = ref(''),
  campuses = ref<Campus[]>([]),
  report = ref<Report>(),
  applied = ref<{ path: string; label: string }>(),
  loading = ref(false);
let sequence = 0, mounted = true;
onBeforeUnmount(() => { mounted = false; sequence++; });
const names: Record<string, string> = {
  paymentCount: '合单支付笔数',
  childOrderCount: '店铺子订单数',
  paidCents: '支付额',
  refundedCents: '成功退款额',
  netCents: '净收款',
  shopDiscountCents: '店铺优惠',
  platformDiscountCents: '平台优惠',
  completed: '完成订单数',
  completedCents: '完成单成交额',
};
function path() {
  return (
    (level.value === 'STORE'
      ? '/merchant/reports?storeId=' + selectedStore.value
      : '/platform/reports?' + (campus.value ? 'campusId=' + campus.value : '')) +
    '&begin=' +
    begin.value +
    '&end=' +
    end.value
  );
}
async function load() {
  const current = ++sequence;
  const snapshot = { path: path(), label: begin.value + ' 至 ' + end.value };
  loading.value = true;
  try {
    const result = await request<Report>(snapshot.path);
    if (!mounted || current !== sequence) return;
    report.value = result;
    applied.value = snapshot;
  } catch (e) {
    if (mounted && current === sequence) notifyError(e);
  } finally {
    if (mounted && current === sequence) loading.value = false;
  }
}
async function download() {
  try {
    const pathValue = applied.value?.path;
    if (!pathValue) return;
    await exportReport(pathValue.replace('/reports?', '/reports/export?'));
  } catch (e) {
    notifyError(e);
  }
}
onMounted(async () => {
  if (session.session?.role === 'PLATFORM_ADMIN')
    try { campuses.value = await request<Campus[]>('/platform/campuses'); }
    catch (e) { if (mounted) notifyError(e); }
  if (mounted) await load();
});
</script>
<template>
  <section class="page-head">
    <div>
      <h1>经营统计</h1>
      <p class="muted">支付与退款按各自发生日期统计，合单支付按结算单去重。</p>
    </div>
    <el-button :disabled="!applied || loading" @click="download">导出 Excel</el-button>
  </section>
  <section class="panel">
    <div class="toolbar">
      <el-select
        v-if="session.session?.role === 'PLATFORM_ADMIN'"
        v-model="level"
        style="width: 160px"
        ><el-option value="STORE" label="当前店铺" /><el-option
          value="PLATFORM"
          label="平台 / 校区" /></el-select
      ><el-select v-if="level === 'PLATFORM'" v-model="campus" clearable placeholder="全部校区"
        ><el-option v-for="c in campuses" :key="c.id" :value="c.id" :label="c.name" /></el-select
      ><input type="date" v-model="begin" /><span>至</span
      ><input type="date" v-model="end" /><el-button type="primary" @click="load">查询</el-button>
    </div>
  </section>
  <p v-if="applied" class="muted" data-testid="report-applied">当前结果：{{ applied.label }}；导出使用当前结果的筛选条件。</p>
  <p v-if="report?.generatedAt" class="muted">数据快照：{{ report.generatedAt }}；支付汇总、每日及店铺分布来自同一数据库快照。</p>
  <div v-if="report" class="metrics">
    <section class="metric" v-for="(value, key) in report.metrics" :key="key">
      <span>{{ names[key] || key }}</span
      ><strong>{{ String(key).endsWith('Cents') ? '¥' + cents(value) : value }}</strong>
    </section>
  </div>
  <section class="panel" v-if="report">
    <h2>每日支付</h2>
    <el-table :data="report.daily"
      ><el-table-column prop="date" label="日期" /><el-table-column
        prop="paymentCount"
        label="支付笔数"
      /><el-table-column label="支付额"
        ><template #default="{ row }">¥{{ cents(row.paidCents) }}</template></el-table-column
      ></el-table
    >
    <h2>店铺分布</h2>
    <el-table :data="report.stores"
      ><el-table-column prop="storeName" label="店铺" /><el-table-column
        prop="childOrderCount"
        label="子订单数"
      /><el-table-column label="支付额"
        ><template #default="{ row }">¥{{ cents(row.paidCents) }}</template></el-table-column
      ></el-table
    >
  </section>
</template>

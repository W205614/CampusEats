<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { ElAlert, ElCheckbox } from 'element-plus';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { selectedStore } from '../context';
import { useSession } from '../stores/session';
import { readPages } from '../../../contracts/paging';
import { orderStatus, states, cents, type Order } from '../../../contracts/platform';
const session = useSession(),
  courier = computed(() => session.session?.role === 'DELIVERER'),
  orders = ref<Order[]>([]),
  pool = ref<
    { id: string; orderId: string; version: string; storeName: string; buildingName: string }[]
  >([]),
  couriers = ref<{ id: string; name: string }[]>([]),
  courierId = ref(''),
  selected = ref<Order>(),
  reason = ref(''),
  code = ref(''),
  busy = ref(false),
  needsRefresh = ref(false),
  exceptionOrder = ref<Order>(),
  exceptionKind = ref<'reassign' | 'exception-cancel'>('reassign'),
  exceptionReason = ref(''),
  exceptionCourier = ref(''),
  handoverConfirmed = ref(false),
  socket = ref<WebSocket>(),
  timer = ref<ReturnType<typeof setInterval>>(),
  loading = ref(false),
  hasMore = ref(false);
let loadedPages = 1, sequence = 0, initialization = 0, mounted = false;
watch([() => session.session?.token, selectedStore], () => {
  sequence++;
  loadedPages = 1;
  orders.value = []; pool.value = []; couriers.value = [];
  selected.value = undefined; exceptionOrder.value = undefined;
  reason.value = ''; code.value = ''; courierId.value = '';
  hasMore.value = false; loading.value = false; busy.value = false; needsRefresh.value = false;
  socket.value?.close();
  if (mounted) void initialize();
}, { flush: 'sync' });
async function load(pages = loadedPages) {
  if (!mounted || !session.session || loading.value || busy.value) return;
  const current = ++sequence;
  const isCurrent = () => mounted && current === sequence;
  const isCourier = courier.value, store = selectedStore.value;
  loading.value = true;
  try {
    const path = isCourier ? '/courier/orders' : '/merchant/stores/' + store + '/orders';
    const [orderResult, relatedResult] = await Promise.allSettled([
      readPages((before, limit) => request<Order[]>(path + '?limit=' + limit + (before ? '&beforeId=' + before : '')), pages, isCurrent),
      isCourier ? request<typeof pool.value>('/courier/pool')
        : request<typeof couriers.value>('/merchant/stores/' + store + '/couriers'),
    ]);
    if (!isCurrent()) return;
    if (orderResult.status === 'rejected') throw orderResult.reason;
    if (relatedResult.status === 'rejected') throw relatedResult.reason;
    const result = orderResult.value, related = relatedResult.value;
    orders.value = result.items;
    hasMore.value = result.hasMore;
    loadedPages = pages;
    if (isCourier) pool.value = related as typeof pool.value;
    else couriers.value = related as typeof couriers.value;
    if (selected.value) selected.value = orders.value.find((o) => o.id === selected.value?.id);
    needsRefresh.value = false;
  } catch (e) {
    if (isCurrent()) notifyError(e);
  } finally {
    if (isCurrent()) loading.value = false;
  }
}
const loadMore = () => load(loadedPages + 1);
const refresh = () => load(1);
async function perform(o: Order, operation: () => Promise<void>) {
  if (!mounted || busy.value || loading.value || needsRefresh.value) return;
  const current = sequence;
  const isCurrent = () => mounted && current === sequence;
  const path = courier.value ? '/courier' : '/merchant';
  busy.value = true;
  needsRefresh.value = true;
  try {
    await operation();
  } catch (e) {
    if (isCurrent()) notifyError(e);
  } finally {
    if (isCurrent()) {
      try {
        const row = await request<Order>(path + '/orders/' + o.id);
        if (isCurrent()) {
          orders.value = orders.value.map((item) => item.id === row.id ? row : item);
          if (selected.value?.id === row.id) selected.value = row;
          needsRefresh.value = false;
        }
      } catch {
        if (isCurrent()) notifyError(new Error('未能确认订单最新状态，请刷新成功后再操作'));
      }
      if (isCurrent()) busy.value = false;
    }
  }
}
async function action(o: Order, name: string) {
  const current = sequence;
  await perform(o, async () => {
    const result = await request<{ verified?: boolean }>(
      (courier.value ? '/courier' : '/merchant') + '/orders/' + o.id + '/actions/' + name,
      'POST',
      {
        version: o.version,
        reason: reason.value || undefined,
        courierId: courierId.value || null,
        code: code.value || null,
      },
    );
    if (result?.verified === false) throw new Error('核销码不正确，错误次数已记录');
    if (mounted && current === sequence) { reason.value = ''; code.value = ''; }
  });
}
async function claim(t: (typeof pool.value)[number]) {
  if (!mounted || busy.value || loading.value || needsRefresh.value) return;
  const current = sequence;
  busy.value = true;
  needsRefresh.value = true;
  try {
    await request('/courier/tasks/' + t.id + '/claim', 'POST', { version: t.version });
  } catch (e) {
    if (mounted && current === sequence) notifyError(e);
  } finally {
    if (mounted && current === sequence) {
      busy.value = false;
      await load();
    }
  }
}
async function unassign(o: Order) {
  await perform(o, async () => {
    await request('/platform/orders/' + o.id + '/unassign', 'POST', { version: o.version });
  });
}
async function reset(o: Order) {
  await perform(o, async () => {
    await request('/platform/orders/' + o.id + '/reset-pickup', 'POST');
  });
}
function openException(o: Order, kind: 'reassign' | 'exception-cancel') {
  // Opening a confirmation does not mutate business state. A websocket/periodic read can begin
  // between a pointer event and Vue's disabled update; do not swallow that click. Submission
  // remains disabled during the read and uses the captured version for server-side validation.
  if (busy.value || needsRefresh.value) return;
  exceptionOrder.value = o;
  exceptionKind.value = kind;
  exceptionReason.value = '';
  exceptionCourier.value = '';
  handoverConfirmed.value = false;
}
async function submitException() {
  const o = exceptionOrder.value, current = sequence;
  if (!o || !exceptionReason.value.trim() || exceptionReason.value.length > 200) return;
  if (exceptionKind.value === 'reassign' && (!exceptionCourier.value || (o.status === 4 && !handoverConfirmed.value))) return;
  await perform(o, async () => {
    await request('/platform/orders/' + o.id + '/' + exceptionKind.value, 'POST', {
      version: o.version,
      reason: exceptionReason.value.trim(),
      ...(exceptionKind.value === 'reassign' ? { courierId: exceptionCourier.value } : {}),
    });
  });
  if (mounted && current === sequence) exceptionOrder.value = undefined;
}
async function initialize() {
  const owner = ++initialization;
  await load();
  if (!mounted || owner !== initialization) return;
  const current = sequence;
  if (mounted && session.session && !courier.value)
    try {
      const result = await request<{ ticket: string }>('/admin/ws-ticket', 'POST');
      if (!mounted || current !== sequence) return;
      socket.value = new WebSocket(
        (location.protocol === 'https:' ? 'wss://' : 'ws://') +
          location.host + '/ws/orders?ticket=' + result.ticket,
      );
      socket.value.onmessage = () => void load();
    } catch {
      /* Polling remains active. */
    }
}
onMounted(async () => {
  mounted = true;
  await initialize();
  if (mounted) timer.value = setInterval(() => void load(), 15000);
});
onUnmounted(() => {
  mounted = false;
  sequence++;
  if (timer.value) clearInterval(timer.value);
  socket.value?.close();
});
</script>
<template>
  <section class="page-head">
    <div>
      <h1>{{ courier ? '抢单与配送' : '店铺订单' }}</h1>
      <p class="muted">
        {{ courier ? '仅展示本校区可抢任务和本人订单。' : '每店独立接单、备餐、配送或自取核销。' }}
        每15秒同步。
      </p>
    </div>
    <el-button :loading="loading" @click="refresh">刷新</el-button>
  </section>
  <section v-if="courier" class="panel">
    <h2>校区抢单池</h2>
    <el-table :data="pool"
      ><el-table-column prop="storeName" label="取餐店铺" /><el-table-column
        prop="buildingName"
        label="送达楼栋"
      /><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button type="primary" :disabled="busy || loading || needsRefresh" @click="claim(row)">抢单</el-button></template
        ></el-table-column
      ></el-table
    >
  </section>
  <section class="panel">
    <el-table :data="orders" @row-click="selected = $event"
      ><el-table-column prop="number" label="订单" min-width="170" /><el-table-column
        prop="storeName"
        label="店铺"
      /><el-table-column label="状态"
        ><template #default="{ row }"
          >{{ orderStatus[row.status]
          }}{{ row.ready && row.status === 3 ? ' · 已备餐' : '' }}</template
        ></el-table-column
      ><el-table-column label="履约"
        ><template #default="{ row }">{{
          row.fulfillment === 'PICKUP' ? '到店自取' : '校区配送'
        }}</template></el-table-column
      ><el-table-column prop="amount" label="应付 / 元" /><el-table-column label="退款"
        ><template #default="{ row }">¥{{ cents(row.refundedCents) }}</template></el-table-column
      ><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button text @click.stop="selected = row">处理订单</el-button></template
        ></el-table-column
      ></el-table
    >
  </section>
  <el-button v-if="hasMore" :loading="loading" @click="loadMore">加载更早订单</el-button>
  <el-drawer
    :model-value="!!selected"
    title="订单详情与操作"
    size="620px"
    @close="selected = undefined"
    ><template v-if="selected"
      ><h2>{{ selected.number }}</h2>
      <p>
        {{ orderStatus[selected.status] }} ·
        {{ selected.fulfillment === 'PICKUP' ? '自取' : '配送' }}
      </p>
      <p>{{ selected.consignee }} {{ selected.phone }} {{ selected.address }}</p>
      <el-alert v-if="needsRefresh" type="warning" :closable="false" title="操作结果尚未确认，请刷新成功后再操作" />
      <el-button v-if="needsRefresh" :loading="loading" :disabled="busy" @click="load()">刷新订单</el-button>
      <el-table :data="selected.details"
        ><el-table-column prop="name" label="商品" /><el-table-column
          prop="number"
          label="数量"
        /><el-table-column label="商品实付"
          ><template #default="{ row }">¥{{ cents(row.lineTotalCents) }}</template></el-table-column
        ></el-table
      >
      <div class="toolbar" style="margin-top: 24px">
        <template v-if="!courier"
          ><el-button
            v-if="selected.status === 2"
            type="primary"
            :disabled="busy || loading || needsRefresh"
            @click="action(selected, 'accept')"
            >接单备餐</el-button
          ><el-button
            v-if="selected.status === 3 && !selected.ready"
            type="primary"
            :disabled="busy || loading || needsRefresh"
            @click="action(selected, 'ready')"
            >备餐完成</el-button
          ><el-button
            v-if="
              selected.status === 3 &&
              selected.ready &&
              selected.fulfillment === 'DELIVERY' &&
              !selected.courierId
            "
            :disabled="busy || loading || needsRefresh"
            @click="action(selected, 'publish')"
            >发布抢单任务</el-button
          ></template
        ><template v-else
          ><el-button
            v-if="selected.status === 3"
            type="primary"
            :disabled="busy || loading || needsRefresh"
            @click="action(selected, 'pickup')"
            >确认取餐</el-button
          ><el-button
            v-if="selected.status === 4"
            type="primary"
            :disabled="busy || loading || needsRefresh"
            @click="action(selected, 'complete')"
            >确认送达</el-button
          ></template
        >
      </div>
      <template
        v-if="
          !courier &&
          selected.status === 3 &&
          selected.ready &&
          selected.fulfillment === 'DELIVERY' &&
          !selected.courierId
        "
        ><h3>人工派单</h3>
        <div class="toolbar">
          <el-select v-model="courierId" placeholder="本校区配送员"
            ><el-option
              v-for="c in couriers"
              :key="c.id"
              :value="c.id"
              :label="c.name" /></el-select
          ><el-button :disabled="!courierId || busy || loading || needsRefresh" @click="action(selected, 'assign')"
            >指派配送</el-button
          >
        </div></template
      ><template
        v-if="
          !courier && selected.status === 3 && selected.ready && selected.fulfillment === 'PICKUP'
        "
        ><h3>自取核销</h3>
        <div class="toolbar">
          <el-input v-model="code" maxlength="8" placeholder="输入用户出示的8位核销码" /><el-button
            :disabled="!code || busy || loading || needsRefresh"
            @click="action(selected, 'verify')"
            >核销</el-button
          ><el-button
            v-if="session.session?.role === 'PLATFORM_ADMIN'"
            text
            :disabled="busy || loading || needsRefresh"
            @click="reset(selected)"
            >重置核销码</el-button
          >
        </div></template
      >
      <div
        class="toolbar"
        v-if="!courier && (selected.status === 2 || (selected.status === 3 && !selected.ready))"
      >
        <el-input v-model="reason" placeholder="拒单或取消原因" maxlength="200" /><el-button
          type="danger"
          :disabled="!reason.trim() || busy || loading || needsRefresh"
          @click="action(selected, selected.status === 2 ? 'reject' : 'cancel')"
          >{{ selected.status === 2 ? '拒单并退款' : '取消并退款' }}</el-button
        >
      </div>
      <el-button
        v-if="
          session.session?.role === 'PLATFORM_ADMIN' && selected.status === 3 && selected.courierId
        "
        :disabled="busy || loading || needsRefresh"
        @click="unassign(selected)"
        >撤回配送分配</el-button
      >
      <div v-if="session.session?.role === 'PLATFORM_ADMIN' && selected.fulfillment === 'DELIVERY' && ((selected.status === 3 && selected.ready) || selected.status === 4)" class="toolbar">
        <el-button v-if="selected.courierId" :disabled="busy || needsRefresh" @click="openException(selected, 'reassign')">平台改派</el-button>
        <el-button type="danger" :disabled="busy || needsRefresh" @click="openException(selected, 'exception-cancel')">异常取消并退款</el-button>
      </div>
      <h3>退款进度</h3>
      <p v-for="r in selected.refunds" :key="r.id">
        ¥{{ cents(r.amountCents) }} · {{ states[r.state] || r.state }}
      </p></template
    ></el-drawer
  >
  <el-dialog :model-value="!!exceptionOrder" :title="exceptionKind === 'reassign' ? '平台改派' : '异常取消订单'" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="exceptionOrder = undefined">
    <template v-if="exceptionOrder">
      <p>{{ exceptionOrder.number }}</p>
      <template v-if="exceptionKind === 'reassign'">
        <el-select v-model="exceptionCourier" placeholder="本校区其他启用配送员" :disabled="busy">
          <el-option v-for="c in couriers.filter((c) => c.id !== exceptionOrder?.courierId)" :key="c.id" :value="c.id" :label="c.name" />
        </el-select>
        <el-checkbox v-if="exceptionOrder.status === 4" v-model="handoverConfirmed" :disabled="busy">我已安排餐品实际交接，配送计时及超时标记继续保留</el-checkbox>
      </template>
      <el-alert v-else type="warning" :closable="false" title="取消仅影响本子订单，退款通过现有模拟任务处理；已备餐库存和已使用优惠券不退回。请先处理已有售后或退款任务。" />
      <el-input v-model="exceptionReason" type="textarea" maxlength="200" show-word-limit placeholder="必填：操作原因" :disabled="busy" />
    </template>
    <template #footer>
      <el-button :disabled="busy" @click="exceptionOrder = undefined">返回</el-button>
      <el-button type="primary" :loading="busy" :disabled="loading || needsRefresh || !exceptionReason.trim() || (exceptionKind === 'reassign' && (!exceptionCourier || (exceptionOrder?.status === 4 && !handoverConfirmed)))" @click="submitException">确认操作</el-button>
    </template>
  </el-dialog>
</template>

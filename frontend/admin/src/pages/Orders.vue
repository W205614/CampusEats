<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue';
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
  socket = ref<WebSocket>(),
  timer = ref<ReturnType<typeof setInterval>>(),
  loading = ref(false),
  hasMore = ref(false);
let loadedPages = 1;
async function load(pages = loadedPages) {
  if (loading.value) return;
  loading.value = true;
  try {
    const path = courier.value ? '/courier/orders' : '/merchant/stores/' + selectedStore.value + '/orders';
    const result = await readPages((before) => request<Order[]>(path + (before ? '?beforeId=' + before : '')), pages);
    orders.value = result.items;
    hasMore.value = result.hasMore;
    loadedPages = pages;
    if (courier.value) pool.value = await request<typeof pool.value>('/courier/pool');
    else
      couriers.value = await request<typeof couriers.value>(
        '/merchant/stores/' + selectedStore.value + '/couriers',
      );
    if (selected.value) selected.value = orders.value.find((o) => o.id === selected.value?.id);
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}
const loadMore = () => load(loadedPages + 1);
const refresh = () => load(1);
async function action(o: Order, name: string) {
  if (busy.value) return;
  busy.value = true;
  try {
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
    reason.value = '';
    code.value = '';
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}
async function claim(t: (typeof pool.value)[number]) {
  try {
    await request('/courier/tasks/' + t.id + '/claim', 'POST', { version: t.version });
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function unassign(o: Order) {
  try {
    await request('/platform/orders/' + o.id + '/unassign', 'POST', { version: o.version });
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function reset(o: Order) {
  try {
    await request('/platform/orders/' + o.id + '/reset-pickup', 'POST');
    await load();
  } catch (e) {
    notifyError(e);
  }
}
onMounted(async () => {
  await load();
  timer.value = setInterval(load, 15000);
  if (!courier.value)
    try {
      const result = await request<{ ticket: string }>('/admin/ws-ticket', 'POST');
      socket.value = new WebSocket(
        (location.protocol === 'https:' ? 'wss://' : 'ws://') +
          location.host +
          '/ws/orders?ticket=' +
          result.ticket,
      );
      socket.value.onmessage = () => load();
    } catch {
      /* Polling remains active. */
    }
});
onUnmounted(() => {
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
          ><el-button type="primary" @click="claim(row)">抢单</el-button></template
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
            :disabled="busy"
            @click="action(selected, 'accept')"
            >接单备餐</el-button
          ><el-button
            v-if="selected.status === 3 && !selected.ready"
            type="primary"
            :disabled="busy"
            @click="action(selected, 'ready')"
            >备餐完成</el-button
          ><el-button
            v-if="
              selected.status === 3 &&
              selected.ready &&
              selected.fulfillment === 'DELIVERY' &&
              !selected.courierId
            "
            @click="action(selected, 'publish')"
            >发布抢单任务</el-button
          ></template
        ><template v-else
          ><el-button
            v-if="selected.status === 3"
            type="primary"
            @click="action(selected, 'pickup')"
            >确认取餐</el-button
          ><el-button
            v-if="selected.status === 4"
            type="primary"
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
          ><el-button :disabled="!courierId" @click="action(selected, 'assign')"
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
            :disabled="!code || busy"
            @click="action(selected, 'verify')"
            >核销</el-button
          ><el-button
            v-if="session.session?.role === 'PLATFORM_ADMIN'"
            text
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
          :disabled="!reason || busy"
          @click="action(selected, selected.status === 2 ? 'reject' : 'cancel')"
          >{{ selected.status === 2 ? '拒单并退款' : '取消并退款' }}</el-button
        >
      </div>
      <el-button
        v-if="
          session.session?.role === 'PLATFORM_ADMIN' && selected.status === 3 && selected.courierId
        "
        @click="unassign(selected)"
        >撤回配送分配</el-button
      >
      <h3>退款进度</h3>
      <p v-for="r in selected.refunds" :key="r.id">
        ¥{{ cents(r.amountCents) }} · {{ states[r.state] || r.state }}
      </p></template
    ></el-drawer
  >
</template>

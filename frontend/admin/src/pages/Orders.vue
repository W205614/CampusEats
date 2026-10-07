<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { api, body, notifyError } from "../api";
import { useSession } from "../stores/session";
import {
  statuses,
  when,
  type Order,
  type Page,
} from "../../../contracts/models";
import { ElMessageBox, ElMessage } from "element-plus";
const store = useSession(),
  rows = ref<Order[]>([]),
  total = ref(0),
  page = ref(1),
  status = ref<number>(),
  query = ref(""),
  busy = ref(false),
  detail = ref<Order>(),
  drawer = ref(false),
  couriers = ref<{ id: string; name: string }[]>([]),
  courier = ref("");
let timer: ReturnType<typeof setInterval>,
  socket: WebSocket | undefined,
  retry: ReturnType<typeof setTimeout> | undefined,
  stopped = false;
const seen = new Set<string>();
async function load() {
  try {
    const params = new URLSearchParams({
      page: String(page.value),
      size: "20",
      query: query.value,
    });
    if (status.value) params.set("status", String(status.value));
    const result = await api<Page<Order>>("/orders?" + params);
    rows.value = result.records;
    total.value = Number(result.total);
  } catch (e) {
    notifyError(e);
  }
}
async function open(row: Order) {
  try {
    detail.value = await api<Order>("/orders/" + row.id);
    courier.value = detail.value.courierId || "";
    drawer.value = true;
  } catch (e) {
    notifyError(e);
  }
}
async function action(action: string) {
  if (!detail.value || busy.value) return;
  let reason = "";
  if (["cancel", "reject"].includes(action)) {
    try {
      reason = (
        await ElMessageBox.prompt(
          "填写原因，已支付订单会进入模拟退款流程。",
          "确认" + (action === "cancel" ? "取消" : "拒单"),
          { inputValidator: (v) => !!v?.trim() || "请填写原因" },
        )
      ).value;
    } catch {
      return;
    }
  }
  busy.value = true;
  try {
    detail.value = await api<Order>(
      "/orders/" + detail.value.id + "/" + action,
      {
        method: "POST",
        body: body({
          version: detail.value.version,
          reason,
          courierId: action === "assign" ? courier.value : null,
        }),
      },
    );
    ElMessage.success("订单状态已更新");
    await load();
  } catch (e) {
    notifyError(e);
    if (detail.value) await open(detail.value);
  } finally {
    busy.value = false;
  }
}
async function connect() {
  if (stopped || store.session?.role === "DELIVERER") return;
  try {
    const { ticket } = await api<{ ticket: string }>("/ws-ticket", {
      method: "POST",
    });
    if (stopped) return;
    socket = new WebSocket(
      (location.protocol === "https:" ? "wss://" : "ws://") +
        location.host +
        "/ws/orders?ticket=" +
        ticket,
    );
    socket.onmessage = (e) => {
      const event = JSON.parse(e.data);
      if (seen.has(event.eventId)) return;
      seen.add(event.eventId);
      if (seen.size > 200) seen.clear();
      load();
    };
    socket.onclose = () => {
      if (!stopped) retry = setTimeout(connect, 5000);
    };
  } catch {
    if (!stopped) retry = setTimeout(connect, 15000);
  }
}
onMounted(async () => {
  await load();
  if (store.session?.role !== "DELIVERER")
    try {
      couriers.value = await api("/couriers");
    } catch {}
  timer = setInterval(load, 15000);
  connect();
});
onUnmounted(() => {
  stopped = true;
  clearInterval(timer);
  clearTimeout(retry);
  socket?.close();
});
</script>
<template>
  <div class="page-heading">
    <div>
      <h2>订单与配送</h2>
      <p class="muted">每15秒自动更新 · 配送员只查看自己的订单</p>
    </div>
    <el-button @click="load">刷新订单</el-button>
  </div>
  <section class="panel">
    <div class="toolbar">
      <el-select
        v-model="status"
        clearable
        placeholder="全部状态"
        style="width: 150px"
        @change="
          page = 1;
          load();
        "
        ><el-option
          v-for="(label, id) in statuses"
          :key="id"
          :label="label"
          :value="Number(id)" /></el-select
      ><el-input
        v-model="query"
        placeholder="订单号前缀 / 完整手机号"
        style="width: 260px"
        clearable
        @keyup.enter="
          page = 1;
          load();
        "
      /><el-button
        @click="
          page = 1;
          load();
        "
        >查询</el-button
      >
    </div>
    <el-table :data="rows" @row-click="open"
      ><el-table-column
        prop="number"
        label="订单号"
        min-width="180"
        show-overflow-tooltip
      /><el-table-column label="餐品" min-width="180"
        ><template #default="{ row }">{{
          row.details?.map((d: any) => d.name + " ×" + d.number).join("、")
        }}</template></el-table-column
      ><el-table-column label="状态" width="110"
        ><template #default="{ row }"
          ><el-tag
            :type="
              row.status === 6
                ? 'info'
                : row.status === 5
                  ? 'success'
                  : 'warning'
            "
            >{{ statuses[row.status] }}</el-tag
          ></template
        ></el-table-column
      ><el-table-column label="金额" width="100"
        ><template #default="{ row }"
          >¥{{ row.amount }}</template
        ></el-table-column
      ><el-table-column
        prop="address"
        label="配送地址"
        min-width="180"
      /><el-table-column label="下单时间" min-width="170"
        ><template #default="{ row }">{{
          when(row.orderTime)
        }}</template></el-table-column
      ><el-table-column width="80"
        ><template #default="{ row }"
          ><el-button text type="primary" @click.stop="open(row)"
            >详情</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-pagination
      class="paging"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="total,prev,pager,next"
      @current-change="load"
    />
  </section>
  <el-drawer v-model="drawer" title="订单详情" size="540px"
    ><template v-if="detail"
      ><h3>
        {{ statuses[detail.status] }}
        <el-tag v-if="detail.deliveryOverdue" type="danger">配送超时</el-tag>
      </h3>
      <p class="muted small">{{ detail.number }}</p>
      <div class="order-summary">
        <div><label>配送地址</label>{{ detail.address || "历史地址未知" }}</div>
        <div>
          <label>收货人</label>{{ detail.consignee }} {{ detail.phone }}
        </div>
        <div><label>下单时间</label>{{ when(detail.orderTime) }}</div>
        <div>
          <label>支付状态</label
          >{{
            detail.payStatus === 2
              ? "已退款"
              : detail.payStatus === 1
                ? "已支付"
                : "待支付"
          }}
        </div>
      </div>
      <div class="status-note" v-if="detail.snapshotSource !== 'ORIGINAL'">
        历史订单地址来源：{{
          detail.snapshotSource === "LEGACY_BACKFILL"
            ? "当前地址补录，不能确认原始地址"
            : "旧记录"
        }}
      </div>
      <el-divider />
      <div class="rank" v-for="item in detail.details" :key="item.id">
        <span>{{ item.name }} ×{{ item.number }}</span
        ><span>¥{{ item.amount }}</span>
      </div>
      <div class="rank">
        <strong>订单总额</strong><strong>¥{{ detail.amount }}</strong>
      </div>
      <p class="muted small" style="margin-top: 14px">
        备注：{{ detail.remark || "无" }}
      </p>
      <div class="status-note" v-if="detail.refund">
        退款：{{ detail.refund.state }} · 已重试 {{ detail.refund.attempts }} 次
      </div>
      <div class="toolbar" style="margin-top: 24px">
        <template v-if="store.session?.role !== 'DELIVERER'"
          ><el-button
            v-if="detail.status === 2"
            type="primary"
            :loading="busy"
            @click="action('accept')"
            >接单备餐</el-button
          ><el-button
            v-if="detail.status === 2"
            :disabled="busy"
            @click="action('reject')"
            >拒单</el-button
          ><el-button
            v-if="detail.status <= 3"
            type="danger"
            plain
            :disabled="busy"
            @click="action('cancel')"
            >取消订单</el-button
          ><template v-if="detail.status === 3"
            ><el-select
              v-model="courier"
              placeholder="选择配送员"
              style="width: 180px"
              ><el-option
                v-for="c in couriers"
                :key="c.id"
                :label="c.name"
                :value="c.id" /></el-select
            ><el-button :disabled="!courier || busy" @click="action('assign')"
              >派单</el-button
            ></template
          ></template
        ><el-button
          v-if="
            detail.status === 3 &&
            detail.courierId &&
            store.session?.role !== 'OPERATOR'
          "
          type="primary"
          :loading="busy"
          @click="action('dispatch')"
          >开始配送</el-button
        ><el-button
          v-if="detail.status === 4 && store.session?.role !== 'OPERATOR'"
          type="primary"
          :loading="busy"
          @click="action('complete')"
          >确认送达</el-button
        >
      </div></template
    ></el-drawer
  >
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow, onHide } from "@dcloudio/uni-app";
import { api, toast, loggedIn, confirm } from "../../api";
import {
  statuses,
  when,
  type Order,
  type Page,
  type Cart,
} from "../../../../contracts/models";
const rows = ref<Order[]>([]),
  page = ref(1),
  total = ref(0),
  detail = ref<Order>(),
  busy = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;
async function load() {
  if (!loggedIn()) return;
  try {
    const result = await api<Page<Order>>(
      "/orders?page=" + page.value + "&size=10",
    );
    rows.value = result.records;
    total.value = Number(result.total);
  } catch (e) {
    toast(e);
  }
}
async function open(row: Order) {
  try {
    detail.value = await api<Order>("/orders/" + row.id);
  } catch (e) {
    toast(e);
  }
}
async function action(row: Order, type: string) {
  if (busy.value) return;
  busy.value = true;
  try {
    if (
      type === "pay" &&
      !(await confirm("模拟支付 ¥" + row.amount + "，不产生真实扣款。"))
    )
      return;
    if (
      type === "cancel" &&
      !(await confirm("取消此订单？已支付订单会进入模拟退款流程。"))
    )
      return;
    if (type === "reorder") {
      const cart = await api<Cart>("/cart");
      const result = await api<{ skipped: string[] }>(
        "/orders/" + row.id + "/reorder",
        "POST",
        { version: cart.cartVersion },
      );
      if (result.skipped.length)
        uni.showModal({
          title: "部分餐品未加入",
          content: result.skipped.join("；"),
          showCancel: false,
        });
      uni.switchTab({ url: "/pages/cart/index" });
      return;
    }
    await api(
      "/orders/" + row.id + "/" + type,
      "POST",
      type === "reminder"
        ? undefined
        : {
            version: row.version,
            reason: type === "cancel" ? "用户取消" : null,
          },
    );
    uni.showToast({
      title:
        type === "pay"
          ? "模拟支付成功"
          : type === "reminder"
            ? "已提醒商家"
            : "已取消",
      icon: "none",
    });
    if (detail.value) await open(row);
    await load();
  } catch (e) {
    toast(e);
    await load();
    if (detail.value) await open(row);
  } finally {
    busy.value = false;
  }
}
onShow(() => {
  load();
  timer = setInterval(load, 15000);
});
onHide(() => clearInterval(timer));

const navTo = (url: string) => uni.navigateTo({ url });
const tabTo = (url: string) => uni.switchTab({ url });
</script>
<template>
  <view class="screen"
    ><view class="row" style="margin-bottom: 25rpx"
      ><view class="section-title" style="margin: 0">我的订单</view
      ><text class="tap" @click="load">刷新</text></view
    ><view class="card" v-for="row in rows" :key="row.id"
      ><view class="row" @click="open(row)"
        ><text class="card-name">{{ statuses[row.status] }}</text
        ><text class="tag" :class="{ gray: row.status === 6 }">{{
          row.deliveryOverdue
            ? "配送超时"
            : row.payStatus === 2
              ? "已退款"
              : row.payStatus === 1
                ? "已支付"
                : "待支付"
        }}</text></view
      ><view class="muted" style="margin: 15rpx 0" @click="open(row)"
        >{{ when(row.orderTime) }}<br />{{
          row.details?.map((d) => d.name + " ×" + d.number).join("、")
        }}</view
      ><view class="row"
        ><text class="price">¥{{ row.amount }}</text
        ><text class="tap" @click="open(row)">查看详情　›</text></view
      ><view class="form-actions"
        ><button
          v-if="row.status === 1"
          class="primary small-button"
          :disabled="busy"
          @click="action(row, 'pay')"
        >
          模拟支付</button
        ><button
          v-if="row.status <= 2"
          class="secondary small-button"
          :disabled="busy"
          @click="action(row, 'cancel')"
        >
          取消</button
        ><button
          v-if="row.status >= 2 && row.status <= 4"
          class="secondary small-button"
          :disabled="busy"
          @click="action(row, 'reminder')"
        >
          催单</button
        ><button
          v-if="row.status >= 5"
          class="secondary small-button"
          :disabled="busy"
          @click="action(row, 'reorder')"
        >
          再来一单
        </button></view
      ></view
    ><view class="empty" v-if="!rows.length"
      ><view class="empty-title">还没有订单</view
      ><view>选一份喜欢的餐，开启今天的好心情。</view></view
    ><view class="row" v-if="total > 10"
      ><button
        class="secondary small-button"
        :disabled="page === 1"
        @click="
          page--;
          load();
        "
      >
        上一页</button
      ><text class="muted">第{{ page }}页</text
      ><button
        class="secondary small-button"
        :disabled="page * 10 >= total"
        @click="
          page++;
          load();
        "
      >
        下一页
      </button></view
    ><view class="modal" v-if="detail" @click="detail = undefined"
      ><view class="card" @click.stop
        ><view class="row"
          ><view class="section-title" style="margin: 0">{{
            statuses[detail.status]
          }}</view
          ><text class="tap" @click="detail = undefined">关闭</text></view
        ><view class="muted" style="font-size: 19rpx; margin-top: 15rpx">{{
          detail.number
        }}</view
        ><view class="line"
          ><view>{{ detail.address || "历史地址未知" }}</view
          ><view class="muted"
            >{{ detail.consignee }} {{ detail.phone }}</view
          ></view
        ><view class="line row" v-for="d in detail.details" :key="d.id"
          ><text>{{ d.name }} ×{{ d.number }}</text
          ><text>¥{{ d.amount }}</text></view
        ><view class="line row"
          ><text>合计</text
          ><text class="price">¥{{ detail.amount }}</text></view
        ><view class="muted" style="margin-top: 15rpx"
          >备注：{{ detail.remark || "无" }}</view
        ><view class="notice" v-if="detail.refund" style="margin-top: 20rpx"
          >模拟退款状态：{{
            detail.refund.state === "SUCCEEDED"
              ? "已完成"
              : detail.refund.state === "FAILED"
                ? "等待商家处理"
                : "正在处理"
          }}</view
        ></view
      ></view
    ></view
  >
</template>

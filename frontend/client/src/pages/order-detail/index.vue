<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { api, loggedIn } from '../../api';
import { useSession } from '../../stores/session';
import { cents, fulfillmentStageLabels, orderStatus, states, type Order } from '../../../../contracts/platform';

const session = useSession();
const order = ref<Order>();
const target = ref('');
const refundId = ref('');
const loading = ref(false);
const error = ref('');
let id = '';
let sequence = 0;
const refunds = computed(() => order.value?.refunds ?? []);
const focusedRefund = computed(() => refunds.value.find((refund) => refund.id === refundId.value));
const displayedRefunds = computed(() => focusedRefund.value ? [focusedRefund.value] : refunds.value);

async function load() {
  if (!/^[1-9][0-9]*$/.test(id)) {
    error.value = '订单链接无效';
    return;
  }
  if (!loggedIn()) {
    error.value = '请登录后查看订单';
    return;
  }
  const request = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const result = await api<Order>('/orders/' + id);
    if (request === sequence) order.value = result;
  } catch (failure) {
    if (request === sequence) error.value = failure instanceof Error ? failure.message : '订单读取失败';
  } finally {
    if (request === sequence) loading.value = false;
  }
}
watch(() => session.session?.token, (token) => {
  sequence++;
  order.value = undefined;
  loading.value = false;
  if (token && id) void load();
  else error.value = '请登录后查看订单';
}, { flush: 'sync' });
onLoad((query) => {
  id = query?.id || '';
  target.value = query?.target || '';
  refundId.value = query?.refundId || '';
});
onShow(() => {
  void load();
});
</script>

<template>
  <view class="screen">
    <view v-if="loading" class="muted">正在加载订单</view>
    <view v-else-if="error" class="card">
      <view>{{ error }}</view>
      <button class="secondary" @click="load">重新加载</button>
    </view>
    <template v-else-if="order">
      <view class="card">
        <view class="section-title">{{ order.campusName }} · {{ order.storeName }}</view>
        <view class="muted">{{ order.number }}</view>
        <view>{{ (order.stage && fulfillmentStageLabels[order.stage]) || orderStatus[order.status] }}</view>
        <view v-if="order.fulfillment === 'PICKUP' && target !== 'refund'" class="notice">
          <view>取餐点：{{ order.pickupPoint || '取餐点未配置' }}</view>
          <view v-if="order.pickupCode && order.status === 3">取餐码：<text class="pickup-code">{{ order.pickupCode }}</text></view>
        </view>
        <button class="secondary" @click="load">刷新订单</button>
      </view>
      <view v-if="target === 'refund' || displayedRefunds.length" class="card">
        <view class="section-title">{{ focusedRefund ? '本次退款' : '退款记录' }}</view>
        <view v-for="refund in displayedRefunds" :key="refund.id" class="refund">
          <view>退款 ¥{{ cents(refund.amountCents) }} · {{ states[refund.state] || refund.state }}</view>
          <view v-for="(item, index) in refund.items" :key="index" class="muted">{{ item.name }} × {{ item.quantity }}</view>
        </view>
        <view v-if="!displayedRefunds.length" class="muted">暂无退款记录</view>
      </view>
      <view v-if="target !== 'refund'" class="card">
        <view class="section-title">餐品</view>
        <view v-for="item in order.details" :key="item.id">{{ item.name }} × {{ item.number }}</view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.pickup-code {
  font-size: 44rpx;
  font-weight: bold;
  letter-spacing: 3rpx;
}
.refund + .refund {
  margin-top: 24rpx;
}
</style>

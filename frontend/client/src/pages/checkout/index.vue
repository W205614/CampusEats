<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { api, toast, loggedIn, uuid, ApiError } from '../../api';
import { useSession } from '../../stores/session';
import { usePagedList } from '../../paged-list';
import type { components } from '../../../../contracts/openapi';
import {
  cents,
  type Cart,
  type CartItem,
  type Address,
  type Coupon,
  type Selection,
  type Quote,
  type Checkout,
  type CheckoutInput,
} from '../../../../contracts/platform';
type CheckoutIssue = components['schemas']['CheckoutIssue'];
const session = useSession(),
  carts = ref<Cart[]>([]),
  addresses = ref<Address[]>([]),
  selections = ref<Selection[]>([]),
  platformCoupon = ref<string | null>(null),
  remark = ref(''),
  quote = ref<Quote>(),
  previousQuote = ref<Quote>(),
  excluded = ref<string[]>([]),
  issue = ref<CheckoutIssue & { message: string }>(),
  busy = ref(false),
  pending = ref<{ key: string; body: CheckoutInput }>();
const selected = computed(() => selections.value.filter((s) => !excluded.value.includes(s.storeId)));
type StoreQuote = Quote['stores'][number];
function priceFields(part: StoreQuote) {
  return [
    part.totalCents,
    part.subtotalCents,
    part.shopDiscountCents,
    part.platformDiscountCents,
    part.deliveryCents,
    part.packagingCents,
  ];
}
function differenceLabel(before: string | number, after: string | number) {
  const difference = Number(after) - Number(before);
  if (difference === 0) return '金额不变';
  return (difference > 0 ? '多付 ¥' : '少付 ¥') + cents(Math.abs(difference));
}
const quoteChanges = computed(() => {
  const before = previousQuote.value;
  const after = quote.value;
  if (!before || !after || before.quoteHash === after.quoteHash) return undefined;

  const remaining = new Map(before.stores.map((part) => [part.storeId, part]));
  const stores = after.stores.flatMap((part) => {
    const old = remaining.get(part.storeId);
    remaining.delete(part.storeId);
    if (old) {
      const oldPrices = priceFields(old);
      if (priceFields(part).every((price, index) => price === oldPrices[index])) return [];
    }
    return [{
      id: part.storeId,
      name: part.storeName,
      campusName: part.campusName,
      before: old?.totalCents ?? '0',
      after: part.totalCents,
      status: old ? '' : '本次加入',
    }];
  });
  for (const part of remaining.values()) {
    stores.push({
      id: part.storeId,
      name: part.storeName,
      campusName: part.campusName,
      before: part.totalCents,
      after: '0',
      status: '本次不结算',
    });
  }
  return { before: before.totalCents, after: after.totalCents, stores };
});
let active = true;
const walletPage = usePagedList<Coupon>(
  () => '/wallet/page?state=AVAILABLE',
  (row) => row.id,
  () => active,
);
const wallet = walletPage.records,
  walletMore = walletPage.hasMore,
  walletLoading = walletPage.loading;
const pendingKey = () => 'checkout-pending-' + session.session?.id;
let sessionVersion = 0,
  loadSequence = 0,
  quoteSequence = 0;
watch(
  () => session.session?.token,
  () => {
    sessionVersion++;
    loadSequence++;
    carts.value = [];
    addresses.value = [];
    walletPage.clear();
    selections.value = [];
    excluded.value = [];
    issue.value = undefined;
    platformCoupon.value = null;
    remark.value = '';
    pending.value = undefined;
    busy.value = false;
    invalidate();
    previousQuote.value = undefined;
  },
  { flush: 'sync' },
);
function invalidate() {
  quoteSequence++;
  if (quote.value) previousQuote.value = quote.value;
  quote.value = undefined;
}
async function load() {
  if (!loggedIn()) return;
  const owner = sessionVersion,
    sequence = ++loadSequence;
  invalidate();
  try {
    pending.value = uni.getStorageSync(pendingKey()) || undefined;
    const selected = (uni.getStorageSync('checkout-stores') as string[]) || [];
    const [loadedCarts, loadedAddresses, walletLoaded] = await Promise.all([
      api<Cart[]>('/carts'),
      api<Address[]>('/addresses'),
      walletPage.load(),
    ]);
    if (!active || owner !== sessionVersion || sequence !== loadSequence || !walletLoaded) return;
    setCarts(loadedCarts.filter((c) => selected.includes(c.storeId) && c.items.length));
    addresses.value = loadedAddresses;
    invalidate();
  } catch (e) {
    if (owner === sessionVersion) showIssue(e);
  }
}
function setCarts(incoming: Cart[]) {
  carts.value = incoming;
  selections.value = incoming.map((c) => {
    const existing = selections.value.find((s) => s.storeId === c.storeId);
    return existing
      ? { ...existing, cartVersion: c.cartVersion }
      : { storeId: c.storeId, cartVersion: c.cartVersion, fulfillment: 'PICKUP', addressId: null, couponId: null };
  });
}
async function refreshCarts() {
  const owner = sessionVersion;
  const incoming = await api<Cart[]>('/carts');
  if (owner !== sessionVersion || !active) return;
  const ids = carts.value.map((cart) => cart.storeId);
  setCarts(incoming.filter((c) => ids.includes(c.storeId) && c.items.length));
}
function toggleStore(id: string) {
  excluded.value = excluded.value.includes(id)
    ? excluded.value.filter((other) => other !== id)
    : [...excluded.value, id];
  issue.value = undefined;
  invalidate();
}
function showIssue(error: unknown) {
  const fields = error instanceof ApiError && error.data && typeof error.data === 'object'
    ? error.data as CheckoutIssue
    : {};
  const coupon = wallet.value.find((row) => row.id === fields.couponId);
  issue.value = {
    ...fields,
    message: (coupon ? coupon.name + '：' : '') + (error instanceof Error ? error.message : '操作失败'),
  };
}
async function changeItem(cart: Cart, item: CartItem, delta: number) {
  if (busy.value) return;
  const owner = sessionVersion;
  busy.value = true;
  invalidate();
  try {
    await api('/stores/' + cart.storeId + '/cart/items', 'POST', {
      cartVersion: cart.cartVersion,
      itemType: item.itemType,
      itemId: item.itemId,
      flavors: item.flavors,
      delta,
    });
    if (owner === sessionVersion) {
      issue.value = undefined;
      await refreshCarts();
    }
  } catch (error) {
    if (owner === sessionVersion) await handleIssue(error);
  } finally {
    if (owner === sessionVersion) busy.value = false;
  }
}
function eligible(c: Cart) {
  return addresses.value.filter((a) => a.campusId === c.campusId);
}
const addressLabel = (a: Address) => a.buildingName + ' ' + a.room + ' ' + a.consignee;
function selectedAddress(c: Cart, id?: string | null) {
  const address = eligible(c).find((a) => a.id === id);
  return address ? addressLabel(address) : '选择本校区收货地址';
}
function coupons(store?: string) {
  return wallet.value.filter(
    (c) => c.state === 'AVAILABLE' && (store ? c.storeId === store : !c.storeId),
  );
}
function fulfillment(s: Selection, e: { detail: { value: unknown } }) {
  const next = Number(e.detail.value) ? 'PICKUP' : 'DELIVERY';
  if (next === s.fulfillment) return;
  s.fulfillment = next;
  s.addressId = null;
  invalidate();
}
function couponChange(s: Selection, e: { detail: { value: unknown } }) {
  s.couponId = coupons(s.storeId)[Number(e.detail.value) - 1]?.id || null;
  invalidate();
}
const input = (): CheckoutInput => ({
  stores: selected.value,
  platformCouponId: platformCoupon.value,
  remark: remark.value,
});
async function preview() {
  if (busy.value || !selected.value.length) return;
  invalidate();
  issue.value = undefined;
  const owner = sessionVersion,
    sequence = quoteSequence;
  busy.value = true;
  try {
    const result = await api<Quote>(
      '/checkouts/preview',
      'POST',
      JSON.parse(JSON.stringify(input())),
    );
    if (owner === sessionVersion && sequence === quoteSequence) quote.value = result;
  } catch (e) {
    if (owner === sessionVersion && sequence === quoteSequence) await handleIssue(e);
  } finally {
    if (owner === sessionVersion) busy.value = false;
  }
}
function finish() {
  uni.removeStorageSync(pendingKey());
  pending.value = undefined;
  uni.switchTab({ url: '/pages/orders/index' });
}
async function send() {
  if (!pending.value) return;
  const owner = sessionVersion;
  await api<Checkout>('/checkouts', 'POST', pending.value.body, {
    'Idempotency-Key': pending.value.key,
  });
  if (owner === sessionVersion) finish();
}
function releaseRejectedRequest(e: unknown) {
  if (e instanceof ApiError && e.status >= 400 && e.status < 500 && ![408, 429].includes(e.status)) {
    uni.removeStorageSync(pendingKey());
    pending.value = undefined;
    invalidate();
  }
}
async function handleIssue(error: unknown) {
  const owner = sessionVersion;
  showIssue(error);
  if (error instanceof ApiError && error.code === 'CART_CHANGED') {
    try {
      await refreshCarts();
    } catch (refreshError) {
      if (owner === sessionVersion) showIssue(refreshError);
    }
  }
}
async function submit() {
  if (!quote.value || busy.value) return;
  const owner = sessionVersion;
  busy.value = true;
  let requote = false;
  try {
    pending.value = {
      key: uuid(),
      body: JSON.parse(JSON.stringify({ ...input(), quoteHash: quote.value.quoteHash })),
    };
    uni.setStorageSync(pendingKey(), pending.value);
    await send();
  } catch (e) {
    if (owner === sessionVersion) {
      releaseRejectedRequest(e);
      await handleIssue(e);
      requote = e instanceof ApiError && e.code === 'QUOTE_CHANGED';
    }
  } finally {
    if (owner === sessionVersion) busy.value = false;
  }
  if (requote && owner === sessionVersion && active) await preview();
}
async function recover() {
  if (!pending.value || busy.value) return;
  const owner = sessionVersion;
  busy.value = true;
  try {
    try {
      await api<Checkout>('/checkouts/by-request/' + pending.value.key);
      if (owner !== sessionVersion) return;
      finish();
    } catch (e) {
      if (owner !== sessionVersion) return;
      if (e instanceof ApiError && e.status === 404) {
        try {
          await send();
        } catch (rejected) {
          if (owner !== sessionVersion) return;
          releaseRejectedRequest(rejected);
          throw rejected;
        }
      } else throw e;
    }
  } catch (e) {
    if (owner === sessionVersion) await handleIssue(e);
  } finally {
    if (owner === sessionVersion) busy.value = false;
  }
}
const navTo = (url: string) => uni.navigateTo({ url });
onShow(() => {
  active = true;
  void load();
});
onHide(() => {
  active = false;
  ++loadSequence;
  ++quoteSequence;
  walletPage.cancel();
});
</script>
<template>
  <view class="screen">
    <view class="title">确认合并订单</view>
    <view class="notice"
      >一次付款，各店分别履约。任一家店校验失败时整批不创建。跨店联合优惠由参与商家承担。</view
    >
    <view v-if="pending" class="card">
      <view>{{ busy ? '正在确认提交结果…' : '上次提交结果尚未确认，请先恢复查询。' }}</view>
      <view v-if="issue && !busy" class="notice">{{ issue.message }}</view>
      <button class="primary" :disabled="busy" :loading="busy" @click="recover">查询并恢复原请求</button>
    </view>
    <template v-else>
      <view class="notice" v-if="issue && !carts.some((c) => c.storeId === issue?.storeId)"
        >{{ issue.message }}</view>
      <view v-if="!carts.length" class="empty">没有可结算的商品。请返回购物车继续选餐。</view>
      <view class="card" v-for="(c, index) in carts" :key="c.storeId">
        <view class="row">
          <text class="section-title">{{ c.campusName }} · {{ c.storeName }}</text>
          <button class="secondary small-button" :disabled="busy" @click="toggleStore(c.storeId)">
            {{ excluded.includes(c.storeId) ? '恢复此店结算' : '暂不结算此店' }}
          </button>
        </view>
        <view v-if="issue?.storeId === c.storeId" class="notice">{{ issue.message }}</view>
        <view v-if="excluded.includes(c.storeId)" class="muted">商品留在购物车，本次不结算。</view>
        <template v-else>
        <picker
          :range="['校区配送', '到店自取']"
          :value="selections[index]?.fulfillment === 'PICKUP' ? 1 : 0"
          @change="fulfillment(selections[index]!, $event)"
        >
          <view class="row">
            <text>履约方式</text>
            <text class="tap"
              >{{ selections[index]?.fulfillment === 'PICKUP' ? '到店自取' : '校区配送' }} ▾</text
            >
          </view>
        </picker>
        <picker
          v-if="selections[index]?.fulfillment === 'DELIVERY'"
          :range="eligible(c).map(addressLabel)"
          @change="
            selections[index]!.addressId = eligible(c)[Number($event.detail.value)]?.id || null;
            invalidate();
          "
        >
          <view class="field-label tap"
            >{{
              selectedAddress(c, selections[index]?.addressId)
            }}
            ▾</view
          >
        </picker>
        <picker
          :range="['不使用店铺券', ...coupons(c.storeId).map((c) => c.name)]"
          @change="couponChange(selections[index]!, $event)"
        >
          <view class="field-label tap"
            >店铺优惠券：{{
              wallet.find((w) => w.id === selections[index]?.couponId)?.name ||
                (selections[index]?.couponId ? '已选优惠券' : '不使用')
            }}
            ▾</view
          >
        </picker>
        <view class="row" v-for="i in c.items" :key="i.id">
          <text :class="issue?.cartItemIds?.includes(i.id) ? 'notice' : 'muted'">
            {{ i.name }} × {{ i.quantity }}
          </text>
          <view>
            <button v-if="i.quantity > 1" class="secondary small-button" :disabled="busy" @click="changeItem(c, i, -1)">减一份</button>
            <button class="secondary small-button" :disabled="busy" @click="changeItem(c, i, -i.quantity)">移除</button>
          </view>
        </view>
        </template>
      </view>
      <view class="card">
        <text class="tap" @click="navTo('/pages/addresses/index')">管理各校区收货地址</text>
        <button
          v-if="walletMore"
          class="secondary small-button"
          :loading="walletLoading"
          :disabled="walletLoading"
          @click="walletPage.load(false)"
          >加载更多可用优惠券</button
        >
        <picker
          :range="['不使用平台券', ...coupons().map((c) => c.name)]"
          @change="
            platformCoupon = coupons()[Number($event.detail.value) - 1]?.id || null;
            invalidate();
          "
        >
          <view class="field-label tap"
            >平台优惠券：{{ wallet.find((w) => w.id === platformCoupon)?.name || (platformCoupon ? '已选优惠券' : '不使用') }} ▾</view
          >
        </picker>
        <textarea v-model="remark" maxlength="100" placeholder="订单备注" @input="invalidate" />
      </view>
      <button class="secondary" :disabled="!selected.length || busy" @click="preview">
        计算报价</button
      >
      <view class="card" v-if="quote">
        <view v-for="p in quote.stores" :key="p.storeId" style="margin-bottom: 28rpx">
          <text style="font-weight: bold">{{ p.storeName }}</text>
          <view class="muted"
            >商品 ¥{{ cents(p.subtotalCents) }} · 店券 −¥{{ cents(p.shopDiscountCents) }} · 平台券
            −¥{{ cents(p.platformDiscountCents) }}</view
          >
          <view class="muted"
            >配送 ¥{{ cents(p.deliveryCents) }} · 包装 ¥{{ cents(p.packagingCents) }}</view
          >
          <view v-if="p.fulfillment === 'PICKUP'" class="muted">取餐点：{{ p.pickupPoint || '取餐点未配置' }}</view>
          <view>本店应付 ¥{{ cents(p.totalCents) }}</view>
        </view>
        <view v-if="quoteChanges" class="notice quote-changes" aria-live="polite">
          <view class="quote-change-title">报价已更新，请核对后提交</view>
          <view v-for="store in quoteChanges.stores" :key="store.id" class="quote-store-change">
            <view class="quote-change-title">
              <text>{{ store.name }}</text>
              <text class="quote-change-campus"> · {{ store.campusName }}</text>
              <text v-if="store.status"> · {{ store.status }}</text>
            </view>
            <view class="quote-difference">
              <text>¥{{ cents(store.before) }} → ¥{{ cents(store.after) }}</text>
              <text class="quote-change-title">{{ differenceLabel(store.before, store.after) }}</text>
            </view>
          </view>
          <view class="quote-difference">
            <text>原合计 ¥{{ cents(quoteChanges.before) }}，现合计 ¥{{ cents(quoteChanges.after) }}</text>
            <text class="quote-change-title">合计{{ differenceLabel(quoteChanges.before, quoteChanges.after) }}</text>
          </view>
        </view>
        <view class="row">
          <text>合计</text>
          <text class="price">¥{{ cents(quote.totalCents) }}</text>
        </view>
        <button class="primary" :loading="busy" @click="submit"> 提交订单，随后付款 </button>
      </view>
    </template>
  </view>
</template>

<style scoped>
.quote-changes {
  margin-bottom: 24rpx;
}
.quote-store-change {
  border-bottom: 1px solid #c9d9ce;
  padding: 12rpx 0;
  margin-bottom: 12rpx;
}
.quote-change-title {
  font-weight: 700;
}
.quote-change-campus {
  font-weight: 400;
}
.quote-difference {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4rpx 16rpx;
}
</style>

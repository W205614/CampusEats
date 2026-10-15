<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { api, toast, loggedIn, uuid, ApiError } from '../../api';
import { useSession } from '../../stores/session';
import {
  cents,
  type Cart,
  type Address,
  type Coupon,
  type Selection,
  type Quote,
  type Checkout,
  type CheckoutInput,
} from '../../../../contracts/platform';
const session = useSession(),
  carts = ref<Cart[]>([]),
  addresses = ref<Address[]>([]),
  wallet = ref<Coupon[]>([]),
  selections = ref<Selection[]>([]),
  platformCoupon = ref<string | null>(null),
  remark = ref(''),
  quote = ref<Quote>(),
  busy = ref(false),
  pending = ref<{ key: string; body: CheckoutInput }>();
const pendingKey = () => 'checkout-pending-' + session.session?.id;
function invalidate() {
  quote.value = undefined;
}
async function load() {
  if (!loggedIn()) return;
  try {
    pending.value = uni.getStorageSync(pendingKey()) || undefined;
    const selected = (uni.getStorageSync('checkout-stores') as string[]) || [];
    [carts.value, addresses.value, wallet.value] = await Promise.all([
      api<Cart[]>('/carts'),
      api<Address[]>('/addresses'),
      api<Coupon[]>('/wallet'),
    ]);
    carts.value = carts.value.filter((c) => selected.includes(c.storeId) && c.items.length);
    selections.value = carts.value.map(
      (c) =>
        selections.value.find(
          (s) => s.storeId === c.storeId && s.cartVersion === c.cartVersion,
        ) || {
          storeId: c.storeId,
          cartVersion: c.cartVersion,
          fulfillment: 'PICKUP',
          addressId: null,
          couponId: null,
        },
    );
    invalidate();
  } catch (e) {
    toast(e);
  }
}
function eligible(c: Cart) {
  return addresses.value.filter((a) => a.campusId === c.campusId);
}
function coupons(store?: string) {
  return wallet.value.filter(
    (c) => c.state === 'AVAILABLE' && (store ? c.storeId === store : !c.storeId),
  );
}
function fulfillment(s: Selection, e: { detail: { value: unknown } }) {
  s.fulfillment = Number(e.detail.value) ? 'PICKUP' : 'DELIVERY';
  s.addressId = null;
  invalidate();
}
function couponChange(s: Selection, e: { detail: { value: unknown } }) {
  s.couponId = coupons(s.storeId)[Number(e.detail.value) - 1]?.id || null;
  invalidate();
}
const input = (): CheckoutInput => ({
  stores: selections.value,
  platformCouponId: platformCoupon.value,
  remark: remark.value,
});
async function preview() {
  busy.value = true;
  try {
    quote.value = await api<Quote>('/checkouts/preview', 'POST', input());
  } catch (e) {
    toast(e);
  } finally {
    busy.value = false;
  }
}
function finish() {
  uni.removeStorageSync(pendingKey());
  pending.value = undefined;
  uni.switchTab({ url: '/pages/orders/index' });
}
async function send() {
  if (!pending.value) return;
  await api<Checkout>('/checkouts', 'POST', pending.value.body, {
    'Idempotency-Key': pending.value.key,
  });
  finish();
}
function releaseRejectedRequest(e: unknown) {
  if (e instanceof ApiError && e.status >= 400 && e.status < 500) {
    uni.removeStorageSync(pendingKey());
    pending.value = undefined;
    quote.value = undefined;
  }
}
async function submit() {
  if (!quote.value || busy.value) return;
  busy.value = true;
  try {
    pending.value = {
      key: uuid(),
      body: JSON.parse(JSON.stringify({ ...input(), quoteHash: quote.value.quoteHash })),
    };
    uni.setStorageSync(pendingKey(), pending.value);
    await send();
  } catch (e) {
    releaseRejectedRequest(e);
    toast(e);
  } finally {
    busy.value = false;
  }
}
async function recover() {
  if (!pending.value || busy.value) return;
  busy.value = true;
  try {
    try {
      await api<Checkout>('/checkouts/by-request/' + pending.value.key);
      finish();
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        try {
          await send();
        } catch (rejected) {
          releaseRejectedRequest(rejected);
          throw rejected;
        }
      }
      else throw e;
    }
  } catch (e) {
    toast(e);
  } finally {
    busy.value = false;
  }
}
const navTo = (url: string) => uni.navigateTo({ url });
onShow(load);
</script>
<template>
  <view class="screen"
    ><view class="title">确认合并订单</view
    ><view class="notice">一次模拟支付，各店分别履约。任一家店校验失败时整批不创建。</view
    ><view v-if="pending" class="card"
      ><view>上次提交结果尚未确认，请先恢复查询。</view
      ><button class="primary" :loading="busy" @click="recover">查询并恢复原请求</button></view
    ><template v-else
      ><view class="card" v-for="(c, index) in carts" :key="c.storeId"
        ><view class="section-title">{{ c.campusName }} · {{ c.storeName }}</view
        ><picker
          :range="['校区配送', '到店自取']"
          :value="selections[index]?.fulfillment === 'PICKUP' ? 1 : 0"
          @change="fulfillment(selections[index]!, $event)"
          ><view class="row"
            ><text>履约方式</text
            ><text class="tap"
              >{{ selections[index]?.fulfillment === 'PICKUP' ? '到店自取' : '校区配送' }} ▾</text
            ></view
          ></picker
        ><picker
          v-if="selections[index]?.fulfillment === 'DELIVERY'"
          :range="eligible(c).map((a) => a.buildingName + ' ' + a.room + ' ' + a.consignee)"
          @change="
            selections[index]!.addressId = eligible(c)[Number($event.detail.value)]?.id || null;
            invalidate();
          "
          ><view class="field-label tap"
            >{{
              eligible(c).find((a) => a.id === selections[index]?.addressId)?.buildingName ||
              '选择本校区收货地址'
            }}
            ▾</view
          ></picker
        ><picker
          :range="['不使用店铺券', ...coupons(c.storeId).map((c) => c.name)]"
          @change="couponChange(selections[index]!, $event)"
          ><view class="field-label tap"
            >店铺优惠券：{{
              wallet.find((w) => w.id === selections[index]?.couponId)?.name || '不使用'
            }}
            ▾</view
          ></picker
        ><view class="muted" v-for="i in c.items" :key="i.id"
          >{{ i.name }} × {{ i.quantity }}</view
        ></view
      ><view class="card"
        ><text class="tap" @click="navTo('/pages/addresses/index')">管理各校区收货地址</text
        ><picker
          :range="['不使用平台券', ...coupons().map((c) => c.name)]"
          @change="
            platformCoupon = coupons()[Number($event.detail.value) - 1]?.id || null;
            invalidate();
          "
          ><view class="field-label tap"
            >平台优惠券：{{ wallet.find((w) => w.id === platformCoupon)?.name || '不使用' }} ▾</view
          ></picker
        ><textarea
          v-model="remark"
          maxlength="100"
          placeholder="订单备注"
          @input="invalidate"
        /></view
      ><button class="secondary" :disabled="!selections.length || busy" @click="preview">
        计算报价</button
      ><view class="card" v-if="quote"
        ><view v-for="p in quote.stores" :key="p.storeId" style="margin-bottom: 28rpx"
          ><text style="font-weight: bold">{{ p.storeName }}</text
          ><view class="muted"
            >商品 ¥{{ cents(p.subtotalCents) }} · 店券 −¥{{ cents(p.shopDiscountCents) }} · 平台券
            −¥{{ cents(p.platformDiscountCents) }}</view
          ><view class="muted"
            >配送 ¥{{ cents(p.deliveryCents) }} · 包装 ¥{{ cents(p.packagingCents) }}</view
          ><view>本店应付 ¥{{ cents(p.totalCents) }}</view></view
        ><view class="row"
          ><text>合计</text><text class="price">¥{{ cents(quote.totalCents) }}</text></view
        ><button class="primary" :loading="busy" @click="submit">
          提交订单，随后模拟支付
        </button></view
      ></template
    ></view
  >
</template>

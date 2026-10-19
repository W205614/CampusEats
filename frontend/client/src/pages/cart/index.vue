<script setup lang="ts">
import { ref, watch } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { api, toast, loggedIn, confirm } from '../../api';
import { useSession } from '../../stores/session';
import type { Cart, CartItem } from '../../../../contracts/platform';
const session = useSession(),
  carts = ref<Cart[]>([]),
  selected = ref<string[]>([]),
  busy = ref(false);
let sessionVersion = 0, loadSequence = 0;
watch(() => session.session?.token, () => {
  sessionVersion++;
  loadSequence++;
  carts.value = [];
  selected.value = [];
  busy.value = false;
}, { flush: 'sync' });
async function load() {
  if (!loggedIn()) return;
  const owner = sessionVersion, sequence = ++loadSequence;
  try {
    const result = await api<Cart[]>('/carts');
    if (owner !== sessionVersion || sequence !== loadSequence) return;
    carts.value = result.filter((c) => c.items.length);
    selected.value = selected.value.filter((id) => carts.value.some((c) => c.storeId === id));
  } catch (e) {
    if (owner === sessionVersion || !session.session) toast(e);
  }
}
function toggle(id: string) {
  selected.value = selected.value.includes(id)
    ? selected.value.filter((x) => x !== id)
    : [...selected.value, id];
}
async function change(c: Cart, item: CartItem, delta: number) {
  if (busy.value) return;
  const owner = sessionVersion;
  busy.value = true;
  try {
    await api('/stores/' + c.storeId + '/cart/items', 'POST', {
      cartVersion: c.cartVersion,
      itemType: item.itemType,
      itemId: item.itemId,
      flavors: item.flavors,
      delta,
    });
    if (owner === sessionVersion) await load();
  } catch (e) {
    if (owner === sessionVersion) {
      toast(e);
      await load();
    }
  } finally {
    if (owner === sessionVersion) busy.value = false;
  }
}
function checkout() {
  uni.setStorageSync('checkout-stores', selected.value);
  uni.navigateTo({ url: '/pages/checkout/index' });
}
async function clearCart(c: Cart) {
  const owner = sessionVersion;
  if (!(await confirm('清空这家店的购物车？'))) return;
  if (owner !== sessionVersion) return;
  try {
    await api('/stores/' + c.storeId + '/cart/clear', 'POST', { version: c.cartVersion });
    if (owner === sessionVersion) await load();
  } catch (e) {
    if (owner === sessionVersion) toast(e);
  }
}
onShow(load);
</script>
<template>
  <view class="screen"
    ><view class="title">我的购物车</view
    ><view class="subtitle">每店购物车独立保存，可勾选多个校区的店铺合并结算。</view
    ><view class="card" v-for="c in carts" :key="c.storeId"
      ><view class="row" @click="toggle(c.storeId)"
        ><text style="font-weight: bold">{{ c.campusName }} · {{ c.storeName }}</text
        ><text class="option" :class="{ selected: selected.includes(c.storeId) }">{{
          selected.includes(c.storeId) ? '已选择' : '选择此店'
        }}</text></view
      ><text class="tap" @click="clearCart(c)">清空本店</text
      ><view v-for="i in c.items" :key="i.id" class="row" style="margin-top: 30rpx"
        ><view
          ><view>{{ i.name }}</view
          ><view class="muted">{{ Object.values(i.flavors || {}).join(' / ') }}</view
          ><text>¥{{ i.price }}</text></view
        ><view class="row"
          ><button class="secondary small-button" :disabled="busy" @click="change(c, i, -1)">
            −</button
          ><text style="margin: 0 20rpx">{{ i.quantity }}</text
          ><button class="secondary small-button" :disabled="busy" @click="change(c, i, 1)">
            ＋
          </button></view
        ></view
      ></view
    ><view v-if="!carts.length" class="empty">购物车还是空的，去挑一份喜欢的餐吧。</view
    ><button v-if="carts.length" class="primary" :disabled="!selected.length" @click="checkout">
      合并结算 {{ selected.length }} 家店
    </button></view
  >
</template>

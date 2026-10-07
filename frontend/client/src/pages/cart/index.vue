<script setup lang="ts">
import { ref, computed } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { api, toast, loggedIn, confirm } from "../../api";
import type { Cart, CartItem } from "../../../../contracts/models";
const cart = ref<Cart>({ cartVersion: "0", items: [] }),
  busy = ref(false),
  subtotal = computed(() =>
    cart.value.items
      .reduce((n, x) => n + Number(x.price || 0) * x.quantity, 0)
      .toFixed(2),
  );
async function load() {
  if (!loggedIn()) return;
  try {
    cart.value = await api<Cart>("/cart");
  } catch (e) {
    toast(e);
  }
}
async function change(item: CartItem, delta: number) {
  if (busy.value) return;
  busy.value = true;
  try {
    cart.value = await api<Cart>("/cart/items", "POST", {
      cartVersion: cart.value.cartVersion,
      itemType: item.itemType,
      itemId: item.itemId,
      flavors: item.flavors,
      delta,
    });
  } catch (e) {
    toast(e);
    await load();
  } finally {
    busy.value = false;
  }
}
async function clear() {
  if (!(await confirm("清空当前账户的购物车？"))) return;
  try {
    cart.value = await api<Cart>("/cart/clear", "POST", {
      version: cart.value.cartVersion,
    });
  } catch (e) {
    toast(e);
    await load();
  }
}
onShow(load);

const navTo = (url: string) => uni.navigateTo({ url });
const tabTo = (url: string) => uni.switchTab({ url });
</script>
<template>
  <view class="screen"
    ><view class="row"
      ><view class="section-title" style="margin-top: 0">我的购物车</view
      ><text v-if="cart.items.length" class="tap" @click="clear"
        >清空</text
      ></view
    ><view class="card" v-for="item in cart.items" :key="item.id"
      ><view class="row"
        ><view
          ><view class="card-name">{{ item.name || "商品已删除" }}</view
          ><view class="muted">{{
            Object.values(item.flavors).join(" / ")
          }}</view
          ><view class="price" style="margin-top: 15rpx"
            >¥{{ item.price || "—" }}</view
          ></view
        ><view class="quantity"
          ><button :disabled="busy" @click="change(item, -1)">−</button
          ><text>{{ item.quantity }}</text
          ><button
            :disabled="busy || item.quantity >= 50"
            @click="change(item, 1)"
          >
            +
          </button></view
        ></view
      ><view class="notice" v-if="item.status !== 1" style="margin: 20rpx 0 0"
        >当前商品不可售，请移除后再结算。</view
      ></view
    ><view v-if="!cart.items.length" class="empty"
      ><view class="empty-title">还没有选餐品</view
      ><view style="margin-bottom: 30rpx">去菜单看看，选一份今天想吃的。</view
      ><button class="primary" @click="tabTo('/pages/menu/index')">
        去点餐
      </button></view
    ><view v-else
      ><view class="notice">餐品小计不含配送与包装费，结算时由服务端确认。</view
      ><view class="sticky-summary row"
        ><view
          ><view class="muted" style="color: #c6cabc">餐品小计</view
          ><text class="price">¥{{ subtotal }}</text></view
        ><button
          class="primary small-button"
          @click="navTo('/pages/checkout/index')"
        >
          去结算
        </button></view
      ></view
    ></view
  >
</template>

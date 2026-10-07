<script setup lang="ts">
import { ref } from "vue";
import { api, toast } from "../../api";
import { useSession } from "../../stores/session";
import type { Session } from "../../../../contracts/models";
const store = useSession(),
  busy = ref(false);
async function demo(account: number) {
  busy.value = true;
  try {
    store.set(await api<Session>("/auth/demo", "POST", { account }));
    uni.removeStorageSync("campus-pending-submit");
    uni.switchTab({ url: "/pages/menu/index" });
  } catch (e) {
    toast(e);
  } finally {
    busy.value = false;
  }
}
async function wechat() {
  busy.value = true;
  try {
    const login = await new Promise<UniApp.LoginRes>((resolve, reject) =>
      uni.login({ provider: "weixin", success: resolve, fail: reject }),
    );
    store.set(await api<Session>("/auth/wechat", "POST", { code: login.code }));
    uni.switchTab({ url: "/pages/menu/index" });
  } catch (e) {
    toast(e);
  } finally {
    busy.value = false;
  }
}
async function logout() {
  try {
    await api("/auth/logout", "POST");
  } catch {}
  store.set(null);
  uni.removeStorageSync("campus-pending-submit");
}

const navTo = (url: string) => uni.navigateTo({ url });
const tabTo = (url: string) => uni.switchTab({ url });
</script>
<template>
  <view class="screen"
    ><view class="hero"
      ><view class="eyebrow">CAMPUS EATS</view
      ><view class="title">校园里的每一餐，<br />都值得好好吃。</view
      ><view class="subtitle">选择独立的演示账户，体验点餐到配送。</view></view
    ><view v-if="store.session" class="card"
      ><view class="row"
        ><view>{{ store.session.name }}</view
        ><button class="secondary small-button" @click="logout">
          退出登录
        </button></view
      ></view
    ><view class="card"
      ><view class="section-title" style="margin-top: 0">演示登录</view
      ><view class="muted" style="margin-bottom: 25rpx"
        >三个账户的购物车、地址和订单彼此独立。</view
      ><button
        v-for="n in 3"
        :key="n"
        class="primary"
        style="margin-bottom: 20rpx"
        :disabled="busy"
        @click="demo(n)"
      >
        进入演示用户 {{ n }}</button
      ><view class="notice"
        >支付与退款均为模拟，无真实资金交易。演示登录仅在演示环境启用。</view
      ></view
    ><view class="card"
      ><view class="section-title" style="margin-top: 0">微信登录</view
      ><view class="muted" style="margin-bottom: 20rpx"
        >需要已配置的微信应用，未配置时会明确提示。</view
      ><button class="secondary" :disabled="busy" @click="wechat">
        使用微信登录
      </button></view
    ></view
  >
</template>

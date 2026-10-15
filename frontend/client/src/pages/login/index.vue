<script setup lang="ts">
import { ref } from "vue";
import { api, toast } from "../../api";
import { useSession } from "../../stores/session";
import type { Session } from "../../../../contracts/models";
const store = useSession(),
  busy = ref(false);
async function demo(account: number) {
  if (busy.value) return;
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
  // #ifdef H5
  uni.showToast({
    title: "微信登录请使用已配置微信应用的小程序；浏览器可选择演示账户。",
    icon: "none",
    duration: 4000,
  });
  return;
  // #endif
  // #ifndef H5
  if (busy.value) return;
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
  // #endif
}
async function logout() {
  if (busy.value) return;
  busy.value = true;
  try {
    await api("/auth/logout", "POST");
  } catch {}
  store.set(null);
  uni.removeStorageSync("campus-pending-submit");
  busy.value = false;
}

</script>
<template>
  <view class="screen entry-screen">
    <view class="entry-brand">CampusEats</view>
    <view class="entry-sheet">
      <view class="entry-intro">
        <view class="entry-title">校园里的每一餐，<br />从这里开始。</view>
        <view class="entry-description">用户点餐登录，选择独立账户体验点餐到配送。</view>
        <view class="entry-footnote">校区选店 · 配送或自取 · 跨店合单</view>
      </view>
      <view class="entry-content">
        <view v-if="store.session" class="entry-current row">
          <view>当前用户：{{ store.session.name }}</view>
          <!-- #ifdef H5 -->
          <component :is="'button'" type="button" class="secondary entry-action entry-logout" :disabled="busy" :aria-busy="busy" @click="logout">退出登录</component>
          <!-- #endif -->
          <!-- #ifndef H5 -->
          <button class="secondary small-button" :disabled="busy" :loading="busy" @click="logout">退出登录</button>
          <!-- #endif -->
        </view>
        <view class="section-title" style="margin-top: 0">演示登录</view>
        <view class="entry-description">三个账户的购物车、地址和订单彼此独立。</view>
        <view class="entry-accounts">
          <!-- #ifdef H5 -->
          <component :is="'button'" v-for="n in 3" :key="n" type="button" class="entry-action" :class="n === 1 ? 'primary' : 'secondary'" :disabled="busy" :aria-busy="busy" @click="demo(n)">{{ busy ? '正在登录…' : '进入演示用户 ' + n }}</component>
          <!-- #endif -->
          <!-- #ifndef H5 -->
          <button v-for="n in 3" :key="n" :class="n === 1 ? 'primary' : 'secondary'" :disabled="busy" :loading="busy" @click="demo(n)">进入演示用户 {{ n }}</button>
          <!-- #endif -->
        </view>
        <view class="entry-simulation">支付与退款均为模拟，无真实资金交易。演示登录仅在演示环境启用。</view>
        <view class="entry-wechat">
          <view class="section-title">微信登录</view>
          <!-- #ifdef H5 -->
          <view class="entry-description">微信登录需在已配置微信应用的小程序中使用。浏览器可选择上方演示账户。</view>
          <component :is="'button'" type="button" class="secondary entry-action" :disabled="busy" :aria-busy="busy" @click="wechat">使用微信登录</component>
          <!-- #endif -->
          <!-- #ifndef H5 -->
          <view class="entry-description">需要已配置的微信应用，未配置时会明确提示。</view>
          <button class="secondary" :disabled="busy" @click="wechat">使用微信登录</button>
          <!-- #endif -->
        </view>
      </view>
    </view>
    <!-- #ifdef H5 -->
    <view class="entry-workbench">
      <view><view class="entry-workbench-title">商家与工作人员</view><view class="entry-description">店长、运营员、配送员和平台管理员使用工作台登录。</view></view>
      <a href="/admin/" class="entry-workbench-link">进入工作台登录 <span aria-hidden="true">→</span></a>
    </view>
    <!-- #endif -->
  </view>
</template>
<style scoped>
.entry-screen { max-width: 1080px; padding: 28px 24px 48px; }
.entry-brand { font-size: 25px; font-weight: 750; color: #173f35; margin-bottom: 28px; letter-spacing: -1px; }
.entry-sheet { display: grid; grid-template-columns: 1fr 1fr; background: #fff; }
.entry-intro { background: #173f35; color: #fff; padding: 48px 36px; display: flex; flex-direction: column; }
.entry-title { font-size: clamp(28px, 3.4vw, 42px); font-weight: 750; line-height: 1.3; margin: 16px 0 24px; letter-spacing: -1px; }
.entry-intro .entry-description { color: #dce8df; }
.entry-description { color: #53615a; font-size: 15px; line-height: 1.7; }
.entry-footnote { margin-top: auto; padding-top: 72px; font-size: 14px; color: #dce8df; }
.entry-content { padding: 36px; }
.entry-content .section-title { font-size: 20px; }
.entry-accounts { display: grid; gap: 12px; margin: 24px 0 20px; }
.entry-accounts button, .entry-wechat button { width: 100%; min-height: 46px; line-height: 46px; font-size: 15px; }
.entry-simulation { margin-top: 20px; color: #53615a; font-size: 13px; line-height: 1.7; }
.entry-wechat { margin-top: 30px; }
.entry-wechat .section-title { margin: 0 0 10px; }
.entry-wechat button { margin-top: 16px; }
.entry-current { background: #e9efe9; padding: 12px; margin-bottom: 24px; flex-wrap: wrap; }
/* #ifdef H5 */
.entry-action { display: block; box-sizing: border-box; width: 100%; min-height: 46px; padding: 0 12px; border: 0; border-radius: 4px; font: inherit; font-size: 15px; line-height: 46px; cursor: pointer; }
.entry-action:disabled { cursor: default; opacity: 0.55; }
.entry-action:focus-visible { outline: 3px solid #173f35; outline-offset: 3px; }
.entry-logout { width: auto; font-size: 14px; }
/* #endif */
.entry-workbench { display: flex; align-items: center; justify-content: space-between; gap: 28px; padding: 28px 0 0; }
.entry-workbench-title { font-size: 16px; font-weight: 650; margin-bottom: 6px; }
.entry-workbench-link { color: #173f35; font-size: 15px; font-weight: 650; white-space: nowrap; text-decoration: none; padding: 12px 0; }
@media (max-width: 650px) {
  .entry-screen { padding: 20px 20px 32px; }
  .entry-brand { margin-bottom: 20px; font-size: 23px; }
  .entry-sheet { grid-template-columns: 1fr; }
  .entry-intro { padding: 24px; }
  .entry-title { margin: 0 0 12px; font-size: 28px; }
  .entry-footnote { padding-top: 20px; }
  .entry-content { padding: 24px; }
  .entry-workbench { flex-direction: column; align-items: flex-start; gap: 6px; }
}
</style>

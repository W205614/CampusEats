<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { api, toast, loggedIn } from '../../api';
import { useSession } from '../../stores/session';
const session = useSession();
const busy = ref(false),
  name = ref(''),
  phone = ref(''),
  code = ref('');
const profile = ref<{
  name: string;
  phone: string | null;
  miniProgramBound: boolean;
  officialAccountBound: boolean;
} | null>(null);
const capabilities = ref({ phoneLoginEnabled: false, oauthEnabled: false, policyVersion: '' });
const templates = ref<{ event: string; templateId: string }[]>([]);
const deliveries = ref<
  { id: string; orderId: string; channel: string; state: string; lastError: string | null }[]
>([]);
const purpose = ref<'BIND' | 'CLOSE'>('BIND');
function openPolicy() {
  uni.navigateTo({ url: '/pages/privacy/index' });
}
onShow(() => {
  void load();
});
async function load() {
  if (!loggedIn()) return;
  try {
    const token = session.session?.token;
    const [p, c, t, d] = await Promise.all([
      api<NonNullable<typeof profile.value>>('/profile'),
      api<typeof capabilities.value>('/auth/identity-capabilities'),
      api<{ templates: typeof templates.value }>('/notifications/templates'),
      api<typeof deliveries.value>('/notifications/deliveries?limit=30'),
    ]);
    if (session.session?.token !== token) return;
    profile.value = p;
    name.value = p.name;
    capabilities.value = c;
    templates.value = t.templates;
    deliveries.value = d;
  } catch (e) {
    toast(e);
  }
}
async function run(action: () => Promise<unknown>) {
  if (busy.value) return;
  busy.value = true;
  try {
    await action();
    await load();
  } catch (e) {
    toast(e);
  } finally {
    busy.value = false;
  }
}
function save() {
  void run(() => api('/profile', 'PUT', { name: name.value }));
}
function send() {
  void run(() => api('/auth/otp/send', 'POST', { phone: phone.value, purpose: purpose.value }));
}
function bind() {
  void run(() => api('/auth/phone/bind', 'POST', { phone: phone.value, code: code.value }));
}
async function close() {
  const confirmed = await new Promise<boolean>((resolve) =>
    uni.showModal({
      title: '申请注销账号',
      content:
        '注销将退出所有会话并清理个人资料。交易和审计记录按运营方保留策略保存。未完成订单、售后或退款会阻止注销。',
      success: (r) => resolve(r.confirm),
      fail: () => resolve(false),
    }),
  );
  if (!confirmed) return;
  await run(async () => {
    await api('/account-closure', 'POST', { phone: phone.value, code: code.value });
    session.set(null);
    uni.removeStorageSync('campus-pending-submit');
    uni.navigateTo({ url: '/pages/login/index' });
  });
}
async function bindWechat() {
  // #ifdef H5
  if (!/MicroMessenger/i.test(navigator.userAgent)) {
    toast(new Error('公众号身份绑定请在微信内打开本页'));
    return;
  }
  if (!capabilities.value.oauthEnabled) {
    toast(new Error('公众号授权尚未配置'));
    return;
  }
  await run(async () => {
    const result = await api<{ url: string }>('/auth/wechat/oauth/start', 'POST');
    window.location.assign(result.url);
  });
  // #endif
  // #ifndef H5
  await run(async () => {
    const login = await new Promise<UniApp.LoginRes>((resolve, reject) =>
      uni.login({ provider: 'weixin', success: resolve, fail: reject }),
    );
    await api('/auth/wechat/bind', 'POST', { code: login.code });
  });
  // #endif
}
async function subscribe() {
  if (process.env.UNI_PLATFORM === 'h5') {
    toast(new Error('请在小程序中授权订阅消息；浏览器可使用站内消息'));
    return;
  }
  const ids = [...new Set(templates.value.map((t) => t.templateId))].slice(0, 3);
  if (!ids.length) return;
  await run(async () => {
    const result = await new Promise<Record<string, unknown>>((resolve, reject) =>
      uni.requestSubscribeMessage({
        tmplIds: ids,
        success: (r) => resolve(r as unknown as Record<string, unknown>),
        fail: reject,
      }),
    );
    for (const id of ids)
      await api('/notifications/subscriptions', 'POST', {
        templateId: id,
        accepted: result[id] === 'accept',
      });
  });
}
function state(value: string) {
  return (
    (
      {
        REQUESTED: '待发送',
        PROCESSING: '发送中',
        ACCEPTED: '渠道已受理',
        SUCCEEDED: '渠道确认送达',
        FAILED: '发送失败',
        UNKNOWN: '结果未知，未自动重发',
        SKIPPED: '未满足发送条件',
      } as Record<string, string>
    )[value] || value
  );
}
</script>
<template>
  <view class="screen">
    <view class="title">个人资料与通知</view>
    <view v-if="profile" class="card">
      <input v-model="name" maxlength="32" placeholder="昵称" aria-label="昵称" /><button
        class="primary"
        :disabled="busy"
        @click="save"
        >保存昵称</button
      >
      <view>手机号：{{ profile.phone || '未绑定' }}</view>
      <view
        >小程序身份：{{ profile.miniProgramBound ? '已绑定' : '未绑定' }} · 公众号身份：{{
          profile.officialAccountBound ? '已绑定' : '未绑定'
        }}</view
      >
      <button class="secondary" :disabled="busy" @click="bindWechat">绑定当前微信身份</button>
    </view>
    <view v-if="capabilities.phoneLoginEnabled" class="card">
      <view class="section-title">手机号与账号注销</view>
      <view class="row"
        ><text class="tap" @click="purpose = 'BIND'">绑定手机号</text
        ><text class="tap" @click="purpose = 'CLOSE'">注销账号</text></view
      >
      <input
        v-model="phone"
        type="number"
        maxlength="11"
        placeholder="手机号"
        aria-label="绑定手机号"
      />
      <input v-model="code" type="number" maxlength="6" placeholder="验证码" aria-label="验证码" />
      <button class="secondary" :disabled="busy" @click="send"
        >获取{{ purpose === 'BIND' ? '绑定' : '注销' }}验证码</button
      >
      <button v-if="purpose === 'BIND'" class="primary" :disabled="busy" @click="bind"
        >确认绑定</button
      >
      <button v-else class="secondary" :disabled="busy" @click="close">验证并申请注销</button>
    </view>
    <view class="card">
      <text class="tap" @click="openPolicy">隐私政策与用户协议</text>
      <button v-if="templates.length" class="secondary" :disabled="busy" @click="subscribe"
        >授权小程序订单提醒</button
      >
      <view class="muted"
        >订阅消息需逐次授权。渠道受理不等于用户已读，订单进度以订单页和站内消息为准。</view
      >
    </view>
    <view class="section-title">最近的外部通知</view>
    <view v-for="item in deliveries" :key="item.id" class="card"
      >订单 {{ item.orderId }} · {{ item.channel }}<view>{{ state(item.state) }}</view
      ><view v-if="item.lastError" class="muted">{{ item.lastError }}</view></view
    >
    <view v-if="!deliveries.length" class="muted">暂无外部通知记录</view>
  </view>
</template>

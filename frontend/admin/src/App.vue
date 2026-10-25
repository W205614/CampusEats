<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useSession } from './stores/session';
import { api, body, notifyError } from './api';
import { selectedStore } from './context';
import { homeFor } from './router';
import { useStorePages } from './store-pages';
import { roles, type Session } from '../../contracts/models';
const session = useSession(),
  router = useRouter(),
  username = ref(''),
  password = ref(''),
  busy = ref(false),
  storesLoaded = ref(false);
const { stores, storeHasMore, storeLoading, loadStores, clearStores } = useStorePages();
let loadSequence = 0;
const role = computed(() => session.session?.role || '');
async function load() {
  const current = ++loadSequence, token = session.session?.token;
  storesLoaded.value = false;
  if (!session.session || session.session.mustChangePassword || role.value === 'DELIVERER') return;
  try {
    if (!await loadStores(true)) return;
    while (selectedStore.value && !stores.value.some(s => s.id === selectedStore.value) && storeHasMore.value) {
      if (!await loadStores()) return;
      if (current !== loadSequence || token !== session.session?.token) return;
    }
    if (current !== loadSequence || token !== session.session?.token) return;
    if (!stores.value.some((s) => s.id === selectedStore.value))
      selectedStore.value = stores.value[0]?.id || '';
    storesLoaded.value = true;
  } catch (e) {
    if (current === loadSequence && token === session.session?.token) notifyError(e);
  }
}
watch(() => session.session?.token, () => { clearStores(); void load(); }, { immediate: true });
async function moreStores() {
  try { await loadStores(); } catch (e) { notifyError(e); }
}
watch(selectedStore, (v) => localStorage.setItem('campus-store', v));
async function login() {
  busy.value = true;
  try {
    const value = await api<Session>('/auth/login', {
      method: 'POST',
      body: body({ username: username.value, password: password.value }),
    });
    session.set(value);
    password.value = '';
    router.push(value.mustChangePassword ? '/password' : homeFor(value.role));
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}
async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' });
  } catch {}
  session.set(null);
  router.push('/');
}
const links = computed(() => {
  if (role.value === 'DELIVERER') return [['/orders', '抢单与配送']];
  const base = [
    ['/', '校区与店铺'],
    ['/orders', '订单履约'],
    ['/operations/aftersales', '售后处理'],
    ['/operations/reviews', '用户评价'],
  ];
  if (role.value !== 'OPERATOR')
    base.push(
      ['/catalog', '商品与配额'],
      ['/marketing', '营销管理'],
      ['/operations/staff', '员工管理'],
      ['/operations/refunds', '退款任务'],
      ['/operations/outbox', '通知任务'],
      ['/reports', '经营统计'],
      ['/operations/audit', '操作记录'],
    );
  if (role.value === 'PLATFORM_ADMIN') base.push(['/operations/buildings', '校区楼栋']);
  return base;
});
</script>
<template>
  <div v-if="!session.session" class="login-shell">
    <div class="login-sheet">
      <section class="login-story">
        <a class="brand" href="/app/" aria-label="CampusEats 用户点餐">CampusEats</a>
        <div class="login-story-copy">
          <h1>把每一餐，<br />安排妥当。</h1>
          <p>从接单、备餐到送达，<br />在各自的岗位，让校园好好吃饭。</p>
        </div>
        <div class="story-note">多校区协作 · 店铺独立履约</div>
      </section>
      <section class="login-form">
        <h2>登录工作台</h2>
        <p class="muted">平台管理员、店长、运营员及配送员统一登录。</p>
        <el-form label-position="top" @submit.prevent="login">
          <el-form-item label="账户" for="staff-username">
            <el-input id="staff-username" v-model="username" autocomplete="username" placeholder="请输入工作账户" required />
          </el-form-item>
          <el-form-item label="密码" for="staff-password">
            <el-input id="staff-password" v-model="password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" required />
          </el-form-item>
          <el-button class="wide" type="primary" native-type="submit" :loading="busy">登录工作台</el-button>
        </el-form>
        <p class="login-help">演示账号使用本机生成的初始密码，首次员工登录须改密。</p>
        <div class="login-customer">
          <span>想给自己点一餐？</span>
          <a href="/app/">用户点餐 <span aria-hidden="true">→</span></a>
        </div>
      </section>
    </div>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand">
        C<span>CampusEats<small>校园供餐平台</small></span>
      </div>
      <nav>
        <router-link v-for="link in links" :key="link[0]" :to="link[0]!">{{ link[1] }}</router-link>
      </nav>
      <div class="sidebar-foot">模拟支付与退款</div>
    </aside>
    <main class="main">
      <header class="topbar">
        <el-select
          v-if="role !== 'DELIVERER'"
          v-model="selectedStore"
          placeholder="选择店铺"
          style="width: 260px"
          ><el-option
            v-for="s in stores"
            :key="s.id"
            :value="s.id"
            :label="s.campusName + ' / ' + s.name" /></el-select
        ><span v-else>校区配送工作台</span>
        <el-button v-if="role !== 'DELIVERER' && storeHasMore" :loading="storeLoading" @click="moreStores">加载更多店铺</el-button>
        <div>
          {{ session.session.name }} · {{ roles[role] }}
          <a href="/app/" style="margin-left: 12px">去点餐</a>
          <el-button text @click="router.push('/password')">改密</el-button
          ><el-button text @click="logout">退出</el-button>
        </div>
      </header>
      <div class="content">
        <router-view v-if="router.currentRoute.value.path === '/password' || (role === 'DELIVERER' ? router.currentRoute.value.path === '/orders' : storesLoaded)" :key="router.currentRoute.value.path + selectedStore" @stores-changed="load" />
        <section v-else class="panel"><p>正在读取授权店铺；加载失败时可重试。</p><el-button @click="load">重新加载店铺</el-button></section>
      </div>
    </main>
  </div>
</template>

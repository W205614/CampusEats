<script setup lang="ts">
import { ref } from "vue";
import { useSession } from "./stores/session";
import { api, body, notifyError } from "./api";
import { roles, type Session } from "../../contracts/models";
import { useRouter } from "vue-router";
const store = useSession(),
  router = useRouter();
const username = ref("admin"),
  password = ref(""),
  busy = ref(false);
async function login() {
  busy.value = true;
  try {
    const value = await api<Session>("/auth/login", {
      method: "POST",
      body: body({ username: username.value, password: password.value }),
    });
    store.set(value);
    password.value = "";
    router.push(value.mustChangePassword ? "/password" : "/");
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}
async function logout() {
  try {
    await api("/auth/logout", { method: "POST" });
  } catch {}
  store.set(null);
  router.push("/");
}
const links = [
  ["/", "运营概览"],
  ["/orders", "订单与配送"],
  ["/catalog/DISH", "菜品管理"],
  ["/catalog/SETMEAL", "套餐管理"],
  ["/catalog/CATEGORY", "分类管理"],
  ["/operations/quotas", "每日供餐"],
  ["/operations/buildings", "配送楼栋"],
  ["/operations/shop", "营业规则"],
  ["/operations/employees", "员工与角色"],
  ["/operations/refund", "退款任务"],
  ["/operations/outbox", "通知任务"],
  ["/reports", "经营统计"],
  ["/operations/audit", "操作记录"],
];
</script>
<template>
  <div v-if="!store.session" class="login-shell">
    <section class="login-story">
      <div class="brand">C<span>CampusEats</span></div>
      <p class="eyebrow">校园里的每一餐</p>
      <h1>供餐有数，<br />配送有序。</h1>
      <p>从每日份数到订单送达，<br />在一个工作台里看清校园供餐。</p>
      <div class="story-note">单校区 · 单店运营 · 模拟支付</div>
    </section>
    <section class="login-form">
      <p class="eyebrow">运营工作台</p>
      <h2>欢迎回来</h2>
      <p class="muted">使用管理员、运营员或配送员账户登录。</p>
      <el-form @submit.prevent="login"
        ><el-form-item label="账户"
          ><el-input
            v-model="username"
            autocomplete="username"
            placeholder="员工账户" /></el-form-item
        ><el-form-item label="密码"
          ><el-input
            v-model="password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="启动脚本生成的初始密码" /></el-form-item
        ><el-button
          type="primary"
          native-type="submit"
          :loading="busy"
          class="wide"
          >登录工作台</el-button
        ></el-form
      >
      <p class="muted small">
        初始账户信息保存在本机 .local/demo-credentials.txt。
      </p>
    </section>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand">
        C<span>CampusEats<small>校园供餐工作台</small></span>
      </div>
      <nav>
        <router-link
          v-for="link in links.filter(
            (x) =>
              store.session?.role === 'ADMIN' ||
              ['/', '/orders'].includes(x[0]!),
          )"
          :key="link[0]"
          :to="link[0]!"
          >{{ link[1] }}</router-link
        >
      </nav>
      <div class="sidebar-foot"><span class="dot"></span> 模拟支付环境</div>
    </aside>
    <main class="main">
      <header class="topbar">
        <span class="muted"
          >校园供餐 /
          {{
            String(router.currentRoute.value.path).includes("orders")
              ? "订单配送"
              : "运营管理"
          }}</span
        >
        <div>
          <span>{{ store.session.name }} · {{ roles[store.session.role] }}</span
          ><el-button text @click="router.push('/password')">改密</el-button
          ><el-button text @click="logout">退出</el-button>
        </div>
      </header>
      <div class="content">
        <router-view :key="router.currentRoute.value.path" />
      </div>
    </main>
  </div>
</template>

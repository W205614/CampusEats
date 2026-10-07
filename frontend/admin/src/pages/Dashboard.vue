<script setup lang="ts">
import { ref, onMounted } from "vue";
import { api, notifyError } from "../api";
import type { Shop } from "../../../contracts/models";
const data = ref<Record<string, string>>({}),
  shop = ref<Shop>();
async function load() {
  try {
    [data.value, shop.value] = await Promise.all([
      api<Record<string, string>>("/dashboard"),
      api<Shop>("/shop"),
    ]);
  } catch (e) {
    notifyError(e);
  }
}
onMounted(load);
</script>
<template>
  <div class="page-heading">
    <div>
      <p class="eyebrow">TODAY AT CAMPUS</p>
      <h2>今天的供餐，一目了然</h2>
      <p class="muted">
        {{ new Date().toLocaleDateString("zh-CN") }} ·
        {{ shop?.accepting ? "正在营业" : "暂停接收新订单" }}
      </p>
    </div>
    <el-button @click="load">刷新概览</el-button>
  </div>
  <div class="cards">
    <div
      v-for="item in [
        ['orders', '今日订单'],
        ['pending', '等待接单'],
        ['delivering', '正在配送'],
        ['turnover', '已完成营业额'],
      ]"
      :key="item[0]"
      class="metric"
    >
      <small>{{ item[1] }}</small
      ><strong
        >{{ item[0] === "turnover" ? "¥" : ""
        }}{{ data[item[0]!] || "0" }}</strong
      >
    </div>
  </div>
  <div class="two-columns">
    <section class="panel">
      <h3>订单工作台</h3>
      <p class="muted">
        待接单 {{ data.pending || 0 }} · 备餐中 {{ data.accepted || 0 }} ·
        已送达 {{ data.completed || 0 }}
      </p>
      <div class="status-note">接单后开始备餐，取消不会自动返还供餐份数。</div>
      <router-link to="/orders"
        ><el-button type="primary">进入订单与配送</el-button></router-link
      >
    </section>
    <section class="panel">
      <h3>需要关注</h3>
      <div class="rank">
        <span>退款失败待处理</span
        ><strong>{{ data.refundFailures || 0 }}</strong>
      </div>
      <div class="rank">
        <span>通知任务积压</span><strong>{{ data.outboxBacklog || 0 }}</strong>
      </div>
      <p class="muted small" style="margin-top: 18px">
        订单列表是业务状态的依据，实时提醒中断时可继续查询。
      </p>
    </section>
  </div>
  <section class="panel">
    <h3>营业信息</h3>
    <p class="muted">
      {{ shop?.hours?.join(" / ") }} · 配送费 ¥{{ shop?.deliveryFee }} ·
      每件包装费 ¥{{ shop?.packagingFee }}
    </p>
    <el-tag type="warning">模拟支付 · 无真实资金交易</el-tag>
  </section>
</template>

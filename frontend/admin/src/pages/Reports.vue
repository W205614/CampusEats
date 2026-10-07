<script setup lang="ts">
import { ref, onMounted } from "vue";
import { api, download, notifyError } from "../api";
const today = new Date(),
  prior = new Date(Date.now() - 29 * 86400000),
  dates = ref([
    prior.toLocaleDateString("sv-SE"),
    today.toLocaleDateString("sv-SE"),
  ]);
const daily = ref<any[]>([]),
  top = ref<any[]>([]);
async function load() {
  if (!dates.value?.length) return;
  try {
    const result = await api<{ daily: any[]; top10: any[] }>(
      "/reports?begin=" + dates.value[0] + "&end=" + dates.value[1],
    );
    daily.value = result.daily;
    top.value = result.top10;
  } catch (e) {
    notifyError(e);
  }
}
async function exportFile() {
  try {
    await download(
      "/reports/export?begin=" + dates.value[0] + "&end=" + dates.value[1],
    );
  } catch (e) {
    notifyError(e);
  }
}
onMounted(load);
</script>
<template>
  <div class="page-heading">
    <div>
      <h2>经营统计</h2>
      <p class="muted">营业额统计已完成且已支付的订单。</p>
    </div>
    <el-button @click="exportFile">导出 Excel</el-button>
  </div>
  <div class="toolbar">
    <el-date-picker
      v-model="dates"
      type="daterange"
      value-format="YYYY-MM-DD"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
    /><el-button type="primary" @click="load">查看统计</el-button>
  </div>
  <div class="two-columns">
    <section class="panel">
      <h3>销售前十</h3>
      <div v-if="!top.length" class="muted">当前日期范围暂无已完成订单。</div>
      <div class="rank" v-for="(item, i) in top" :key="item.name">
        <span
          ><span class="muted">{{ String(i + 1).padStart(2, "0") }}　</span
          >{{ item.name }}</span
        ><strong>{{ item.quantity }} 份</strong>
      </div>
    </section>
    <section class="panel">
      <h3>范围汇总</h3>
      <div class="rank">
        <span>订单数</span
        ><strong>{{ daily.reduce((n, x) => n + Number(x.orders), 0) }}</strong>
      </div>
      <div class="rank">
        <span>完成订单</span
        ><strong>{{
          daily.reduce((n, x) => n + Number(x.completed), 0)
        }}</strong>
      </div>
      <div class="rank">
        <span>营业额</span
        ><strong
          >¥{{
            daily.reduce((n, x) => n + Number(x.turnover), 0).toFixed(2)
          }}</strong
        >
      </div>
      <div class="rank">
        <span>新增用户</span
        ><strong>{{
          daily.reduce((n, x) => n + Number(x.newUsers), 0)
        }}</strong>
      </div>
    </section>
  </div>
  <section class="panel">
    <h3>每日明细</h3>
    <el-table :data="daily"
      ><el-table-column prop="date" label="日期" /><el-table-column
        prop="orders"
        label="订单" /><el-table-column
        prop="completed"
        label="已完成" /><el-table-column label="营业额"
        ><template #default="{ row }"
          >¥{{ Number(row.turnover).toFixed(2) }}</template
        ></el-table-column
      ><el-table-column prop="newUsers" label="新增用户"
    /></el-table>
  </section>
</template>

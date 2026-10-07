<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { api, body, notifyError } from "../api";
import { ElMessage } from "element-plus";
import { roles, when, type Shop } from "../../../contracts/models";
const kind = String(useRoute().params.kind),
  names: Record<string, string> = {
    quotas: "每日供餐",
    buildings: "配送楼栋",
    shop: "营业规则",
    employees: "员工与角色",
    refund: "退款任务",
    outbox: "通知任务",
    audit: "操作记录",
  };
const rows = ref<any[]>([]),
  date = ref(new Date().toLocaleDateString("sv-SE")),
  dialog = ref(false),
  id = ref<string>(),
  saving = ref(false),
  form = ref<any>({}),
  shop = ref<Shop>(),
  page = ref(1);
async function load() {
  try {
    if (kind === "shop") {
      shop.value = await api<Shop>("/shop");
      return;
    }
    rows.value = await api(
      kind === "quotas"
        ? "/quotas?date=" + date.value
        : kind === "refund" || kind === "outbox"
          ? "/tasks/" + kind
          : kind === "audit"
            ? "/audit?page=" + page.value
            : "/" + kind,
    );
  } catch (e) {
    notifyError(e);
  }
}
function edit(row?: any) {
  id.value = row?.id || row?.dishId;
  form.value =
    kind === "employees"
      ? {
          username: row?.username || "",
          name: row?.name || "",
          role: row?.role || "OPERATOR",
          enabled: row ? row.status === 1 : true,
          password: "",
        }
      : kind === "buildings"
        ? { name: row?.name || "", enabled: row?.enabled ?? true }
        : {
            dishId: row.dishId,
            total: Number(row.total),
            defaultQuota: false,
            name: row.name,
          };
  dialog.value = true;
}
async function save() {
  saving.value = true;
  try {
    const input = { ...form.value };
    if (kind === "employees" && !input.password) delete input.password;
    await api(
      kind === "quotas"
        ? "/quotas?date=" + date.value
        : "/" + kind + (id.value ? "/" + id.value : ""),
      {
        method: kind === "quotas" || id.value ? "PUT" : "POST",
        body: body(input),
      },
    );
    dialog.value = false;
    await load();
    ElMessage.success("保存成功");
  } catch (e) {
    notifyError(e);
  } finally {
    saving.value = false;
  }
}
async function saveShop() {
  saving.value = true;
  try {
    await api("/shop", { method: "PUT", body: body(shop.value) });
    await load();
    ElMessage.success("营业规则已更新");
  } catch (e) {
    notifyError(e);
  } finally {
    saving.value = false;
  }
}
async function retry(row: any) {
  try {
    await api("/tasks/" + kind + "/" + row.id + "/retry", { method: "POST" });
    await load();
  } catch (e) {
    notifyError(e);
  }
}
onMounted(load);
</script>
<template>
  <div class="page-heading">
    <div>
      <h2>{{ names[kind] }}</h2>
      <p class="muted">
        {{
          kind === "quotas"
            ? "下单预占，接单后消耗；接单前取消返还。"
            : kind === "refund"
              ? "只有模拟退款成功后，订单才标记已退款。"
              : "配置校园运营，查看处理记录。"
        }}
      </p>
    </div>
    <el-button
      v-if="['employees', 'buildings'].includes(kind)"
      type="primary"
      @click="edit()"
      >新增{{ kind === "employees" ? "员工" : "楼栋" }}</el-button
    ><el-button v-else @click="load">刷新</el-button>
  </div>
  <section class="panel" v-if="kind === 'shop' && shop">
    <el-form label-position="top"
      ><div class="two-columns">
        <el-form-item label="人工营业开关"
          ><el-switch
            v-model="shop.open"
            active-text="营业"
            inactive-text="关闭" /></el-form-item
        ><el-form-item label="联系电话"
          ><el-input v-model="shop.phone" /></el-form-item
        ><el-form-item label="每单配送费（元）"
          ><el-input v-model="shop.deliveryFee" type="number" /></el-form-item
        ><el-form-item label="每件包装费（元）"
          ><el-input v-model="shop.packagingFee" type="number"
        /></el-form-item>
      </div>
      <h3>每日营业时段</h3>
      <p class="muted small">
        北京时间，格式如 11:00-14:00；全天为 00:00-24:00。
      </p>
      <div class="form-row" v-for="(_, i) in shop.hours" :key="i">
        <el-input v-model="shop.hours[i]" placeholder="11:00-14:00" /><el-button
          @click="shop.hours.splice(i, 1)"
          :disabled="shop.hours.length === 1"
          >移除</el-button
        >
      </div>
      <el-button
        @click="shop.hours.push('17:00-20:00')"
        :disabled="shop.hours.length >= 8"
        >增加时段</el-button
      >
      <div class="status-note">
        关店后停止接收新订单，已创建订单仍可在支付期限内付款。
      </div>
      <el-button type="primary" :loading="saving" @click="saveShop"
        >保存营业规则</el-button
      ></el-form
    >
  </section>
  <section class="panel" v-else>
    <div class="toolbar" v-if="kind === 'quotas'">
      <el-date-picker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        @change="load"
      /><span class="muted small">默认配额只影响尚未建立配额记录的日期。</span>
    </div>
    <el-table :data="rows"
      ><template v-if="kind === 'quotas'"
        ><el-table-column prop="name" label="菜品" /><el-table-column
          prop="defaultQuota"
          label="默认份数"
        /><el-table-column prop="total" label="当日总份数" /><el-table-column
          prop="reserved"
          label="预占"
        /><el-table-column prop="consumed" label="已消耗" /><el-table-column
          prop="remaining"
          label="剩余"
        /><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button text type="primary" @click="edit(row)"
              >调整配额</el-button
            ></template
          ></el-table-column
        ></template
      ><template v-else-if="kind === 'buildings'"
        ><el-table-column prop="name" label="楼栋名称" /><el-table-column
          label="配送状态"
          ><template #default="{ row }"
            ><el-tag :type="row.enabled ? 'success' : 'info'">{{
              row.enabled ? "可配送" : "暂停配送"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button text type="primary" @click="edit(row)"
              >编辑</el-button
            ></template
          ></el-table-column
        ></template
      ><template v-else-if="kind === 'employees'"
        ><el-table-column prop="name" label="姓名" /><el-table-column
          prop="username"
          label="账户"
        /><el-table-column label="角色"
          ><template #default="{ row }">{{
            roles[row.role]
          }}</template></el-table-column
        ><el-table-column label="状态"
          ><template #default="{ row }"
            >{{ row.status === 1 ? "启用" : "禁用"
            }}{{ row.mustChangePassword ? " · 待改密" : "" }}</template
          ></el-table-column
        ><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button text type="primary" @click="edit(row)"
              >编辑</el-button
            ></template
          ></el-table-column
        ></template
      ><template v-else-if="kind === 'audit'"
        ><el-table-column
          prop="action"
          label="操作"
          min-width="160"
        /><el-table-column
          prop="actorType"
          label="操作者类型"
        /><el-table-column prop="actorId" label="操作者" /><el-table-column
          prop="targetId"
          label="对象"
        /><el-table-column
          prop="detail"
          label="说明"
          min-width="180"
        /><el-table-column label="时间" min-width="170"
          ><template #default="{ row }">{{
            when(row.createdAt)
          }}</template></el-table-column
        ></template
      ><template v-else
        ><el-table-column prop="id" label="任务" /><el-table-column
          :prop="kind === 'refund' ? 'orderId' : 'aggregateId'"
          label="订单"
        /><el-table-column prop="state" label="状态" /><el-table-column
          prop="attempts"
          label="失败次数"
        /><el-table-column prop="lastError" label="失败原因" /><el-table-column
          label="下一次处理"
          min-width="170"
          ><template #default="{ row }">{{
            when(row.nextAttemptAt)
          }}</template></el-table-column
        ><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button
              v-if="row.state === 'FAILED'"
              text
              type="primary"
              @click="retry(row)"
              >重新处理</el-button
            ></template
          ></el-table-column
        ></template
      ></el-table
    >
    <div class="toolbar" v-if="kind === 'audit'" style="margin-top: 20px">
      <el-button
        :disabled="page === 1"
        @click="
          page--;
          load();
        "
        >上一页</el-button
      ><span>第{{ page }}页</span
      ><el-button
        :disabled="rows.length < 20"
        @click="
          page++;
          load();
        "
        >下一页</el-button
      >
    </div>
  </section>
  <el-dialog v-model="dialog" :title="id ? '编辑' : '新增'" width="480px"
    ><el-form label-position="top"
      ><template v-if="kind === 'employees'"
        ><el-form-item label="姓名"
          ><el-input v-model="form.name" /></el-form-item
        ><el-form-item label="账户"
          ><el-input v-model="form.username" /></el-form-item
        ><el-form-item label="角色"
          ><el-select v-model="form.role"
            ><el-option
              v-for="(name, role) in roles"
              v-show="role !== 'USER'"
              :key="role"
              :value="role"
              :label="name" /></el-select></el-form-item
        ><el-form-item
          :label="id ? '重置密码（留空则不改）' : '初始密码（至少12个字符）'"
          ><el-input
            v-model="form.password"
            type="password"
            show-password /></el-form-item
        ><el-form-item label="启用"
          ><el-switch v-model="form.enabled"
        /></el-form-item>
        <p class="muted small">
          角色、状态或密码变化会撤销该员工已有会话。
        </p></template
      ><template v-else-if="kind === 'buildings'"
        ><el-form-item label="楼栋名称"
          ><el-input v-model="form.name" /></el-form-item
        ><el-form-item label="允许配送"
          ><el-switch v-model="form.enabled" /></el-form-item></template
      ><template v-else
        ><el-form-item :label="form.name + ' · 总份数'"
          ><el-input-number
            v-model="form.total"
            :min="0"
            :max="100000" /></el-form-item
        ><el-form-item label="调整范围"
          ><el-radio-group v-model="form.defaultQuota"
            ><el-radio :value="false">指定日期</el-radio
            ><el-radio :value="true">默认每日份数</el-radio></el-radio-group
          ></el-form-item
        ></template
      ></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存</el-button
      ></template
    ></el-dialog
  >
</template>

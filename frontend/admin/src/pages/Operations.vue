<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { selectedStore } from '../context';
import { useSession } from '../stores/session';
import { roles } from '../../../contracts/models';
import {
  cents,
  states,
  type Campus,
  type Store,
  type Staff,
  type Aftersale,
  type Refund,
  type Review,
} from '../../../contracts/platform';
const route = useRoute(),
  session = useSession(),
  kind = String(route.params.kind),
  platform = computed(() => session.session?.role === 'PLATFORM_ADMIN'),
  staff = ref<Staff[]>([]),
  campuses = ref<Campus[]>([]),
  stores = ref<Store[]>([]),
  campus = ref(''),
  buildings = ref<{ id: string; name: string; enabled: boolean }[]>([]),
  aftersales = ref<Aftersale[]>([]),
  refunds = ref<Refund[]>([]),
  reviews = ref<Review[]>([]),
  audit = ref<{ id: string; actorId: string; action: string; detail: string; createdAt: string }[]>(
    [],
  ),
  dialog = ref(false),
  editing = ref(''),
  buildingName = ref(''),
  decision = ref(''),
  reply = ref(''),
  imageUrl = ref('');
const form = ref({
  name: '',
  username: '',
  role: 'OPERATOR',
  storeId: selectedStore.value,
  campusId: '',
  enabled: true,
  password: '',
});
const titles: Record<string, string> = {
  staff: '员工与角色',
  buildings: '校区楼栋',
  aftersales: '售后与平台介入',
  refunds: '退款任务',
  outbox: '通知任务',
  reviews: '订单评价',
  audit: '操作记录',
};
const base = () => '/merchant/stores/' + selectedStore.value;
async function load() {
  try {
    if (kind === 'staff')
      staff.value = await request<Staff[]>(platform.value ? '/platform/staff' : base() + '/staff');
    if (kind === 'buildings' && campus.value)
      buildings.value = await request<typeof buildings.value>(
        '/platform/campuses/' + campus.value + '/buildings',
      );
    if (kind === 'aftersales')
      aftersales.value = await request<Aftersale[]>(
        platform.value ? '/platform/aftersales' : base() + '/aftersales',
      );
    if (kind === 'outbox')
      refunds.value = await request<Refund[]>(
        platform.value ? '/platform/events' : base() + '/events',
      );
    if (kind === 'refunds')
      refunds.value = await request<Refund[]>(
        platform.value ? '/platform/refunds' : base() + '/refunds',
      );
    if (kind === 'reviews')
      reviews.value = (await request<{ records: Review[] }>(base() + '/reviews')).records;
    if (kind === 'audit')
      audit.value = await request<typeof audit.value>(
        platform.value ? '/platform/audit' : base() + '/audit',
      );
  } catch (e) {
    notifyError(e);
  }
}
function edit(s?: Staff) {
  editing.value = s?.id || '';
  form.value = {
    name: s?.name || '',
    username: s?.username || '',
    role: s?.role || 'OPERATOR',
    storeId: s?.storeId || selectedStore.value,
    campusId: s?.campusId || campus.value,
    enabled: s ? s.status === 1 : true,
    password: '',
  };
  dialog.value = true;
}
async function saveStaff() {
  try {
    const f = form.value;
    await request(
      (platform.value ? '/platform/staff' : '/merchant/staff') +
        (editing.value ? '/' + editing.value : ''),
      editing.value ? 'PUT' : 'POST',
      {
        ...f,
        password: f.password || null,
        storeId: ['SHOP_MANAGER', 'OPERATOR'].includes(f.role) ? f.storeId : null,
        campusId: f.role === 'DELIVERER' ? f.campusId : null,
      },
    );
    dialog.value = false;
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function addBuilding() {
  try {
    await request('/platform/campuses/' + campus.value + '/buildings', 'POST', {
      name: buildingName.value,
      enabled: true,
    });
    buildingName.value = '';
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function toggleBuilding(b: (typeof buildings.value)[number]) {
  try {
    await request('/platform/campuses/' + campus.value + '/buildings/' + b.id, 'PUT', {
      name: b.name,
      enabled: !b.enabled,
    });
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function decide(r: Aftersale, approve: boolean) {
  try {
    if (!decision.value.trim()) throw new Error('请先填写处理说明');
    await request(
      (platform.value && r.state === 'ESCALATED' ? '/platform' : '/merchant') +
        '/aftersales/' +
        r.id +
        '/decision',
      'POST',
      { version: r.version, approve, reason: decision.value },
    );
    decision.value = '';
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function retry(r: Refund) {
  try {
    await request(
      '/merchant/' + (kind === 'outbox' ? 'events' : 'refunds') + '/' + r.id + '/retry',
      'POST',
    );
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function replyReview(r: Review) {
  try {
    await request('/merchant/reviews/' + r.id + '/reply', 'POST', { text: reply.value });
    reply.value = '';
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function hide(r: Review) {
  try {
    if (!decision.value.trim()) throw new Error('请填写隐藏原因');
    await request('/platform/reviews/' + r.id + '/hide', 'POST', { text: decision.value });
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function viewEvidence(id: string) {
  try {
    const response = await fetch('/api/v1/evidence/' + id, {
      headers: { Authorization: 'Bearer ' + session.session?.token },
    });
    if (!response.ok) throw new Error('无权读取该图片');
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value);
    imageUrl.value = URL.createObjectURL(await response.blob());
  } catch (e) {
    notifyError(e);
  }
}
onMounted(async () => {
  if (platform.value) {
    [campuses.value, stores.value] = await Promise.all([
      request<Campus[]>('/platform/campuses'),
      request<Store[]>('/merchant/stores'),
    ]);
    campus.value = campuses.value[0]?.id || '';
  }
  await load();
});
onUnmounted(() => {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value);
});
</script>
<template>
  <section class="page-head">
    <div>
      <h1>{{ titles[kind] }}</h1>
      <p class="muted">按当前身份与店铺权限展示，重要操作留有审计记录。</p>
    </div>
    <el-button @click="load">刷新</el-button>
  </section>
  <section class="panel" v-if="kind === 'staff'">
    <el-button type="primary" @click="edit()">新增员工</el-button
    ><el-table :data="staff"
      ><el-table-column prop="username" label="账户" /><el-table-column
        prop="name"
        label="姓名"
      /><el-table-column label="角色"
        ><template #default="{ row }">{{ roles[row.role] }}</template></el-table-column
      ><el-table-column label="状态"
        ><template #default="{ row }">{{
          row.status === 1 ? '启用' : '停用'
        }}</template></el-table-column
      ><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button text @click="edit(row)">编辑 / 重置密码</el-button></template
        ></el-table-column
      ></el-table
    >
  </section>
  <section class="panel" v-if="kind === 'buildings'">
    <div class="toolbar">
      <el-select v-model="campus" @change="load"
        ><el-option v-for="c in campuses" :key="c.id" :value="c.id" :label="c.name" /></el-select
      ><el-input v-model="buildingName" placeholder="新增楼栋名称" style="width: 200px" /><el-button
        :disabled="!buildingName"
        @click="addBuilding"
        >新增楼栋</el-button
      >
    </div>
    <el-table :data="buildings"
      ><el-table-column prop="name" label="楼栋" /><el-table-column label="配送"
        ><template #default="{ row }">{{
          row.enabled ? '启用' : '停用'
        }}</template></el-table-column
      ><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button text @click="toggleBuilding(row)">{{
            row.enabled ? '停用' : '启用'
          }}</el-button></template
        ></el-table-column
      ></el-table
    >
  </section>
  <section class="panel" v-if="kind === 'aftersales'">
    <p>店铺处理申请；被拒绝或超过48小时未处理的申请可由用户申请平台介入。</p>
    <el-input
      v-model="decision"
      placeholder="本次处理说明"
      maxlength="200"
      style="margin-bottom: 20px"
    />
    <article v-for="a in aftersales" :key="a.id" class="panel">
      <div class="toolbar">
        <strong>申请 {{ a.id }} · 订单 {{ a.orderId }}</strong
        ><el-tag>{{ states[a.state] || a.state }}</el-tag
        ><strong>¥{{ cents(a.amountCents) }}</strong>
      </div>
      <p>{{ a.reason }}</p>
      <p v-if="a.decision">处理说明：{{ a.decision }}</p>
      <p v-for="i in a.items" :key="i.detailId">{{ i.name }} × {{ i.quantity }}</p>
      <div class="toolbar">
        <el-button v-for="(e, index) in a.evidence" :key="e.id" @click="viewEvidence(e.id)"
          >查看举证 {{ index + 1 }}</el-button
        >
      </div>
      <div class="toolbar" v-if="a.state === 'PENDING' || (platform && a.state === 'ESCALATED')">
        <el-button type="primary" @click="decide(a, true)">同意退款 / 取消</el-button
        ><el-button @click="decide(a, false)">拒绝并说明</el-button>
      </div>
    </article>
    <p v-if="!aftersales.length" class="muted">暂无售后申请。</p>
  </section>
  <section class="panel" v-if="kind === 'refunds' || kind === 'outbox'">
    <el-table :data="refunds"
      ><el-table-column prop="id" :label="kind === 'outbox' ? '通知编号' : '退款编号'" /><el-table-column
        :prop="kind === 'outbox' ? 'aggregateId' : 'orderId'"
        label="订单"
      /><el-table-column v-if="kind === 'outbox'" prop="eventType" label="通知类型" /><el-table-column v-else label="金额"
        ><template #default="{ row }">¥{{ cents(row.amountCents) }}</template></el-table-column
      ><el-table-column label="状态"
        ><template #default="{ row }">{{ states[row.state] || row.state }}</template></el-table-column
      ><el-table-column prop="attempts" label="失败次数" /><el-table-column
        prop="lastError"
        label="原因"
      /><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button v-if="row.state === 'FAILED'" text @click="retry(row)"
            >恢复重试</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </section>
  <section class="panel" v-if="kind === 'reviews'">
    <div class="toolbar">
      <el-input v-model="reply" placeholder="商家回复内容" maxlength="1000" /><el-input
        v-if="platform"
        v-model="decision"
        placeholder="平台隐藏原因"
        maxlength="200"
      />
    </div>
    <article class="panel" v-for="r in reviews" :key="r.id">
      <strong>{{ r.rating }} 星 · 订单 {{ r.orderId }}</strong>
      <p>{{ r.content }}</p>
      <p v-if="r.reply">商家回复：{{ r.reply }}</p>
      <p v-if="r.hidden">平台已隐藏：{{ r.hideReason }}</p>
      <el-button v-if="!r.reply" :disabled="!reply" @click="replyReview(r)">回复评价</el-button
      ><el-button v-if="platform && !r.hidden" @click="hide(r)">隐藏违规评价</el-button>
    </article>
  </section>
  <section class="panel" v-if="kind === 'audit'">
    <el-table :data="audit"
      ><el-table-column prop="createdAt" label="时间" min-width="180" /><el-table-column
        prop="actorId"
        label="操作人" /><el-table-column prop="action" label="操作" /><el-table-column
        prop="detail"
        label="说明"
    /></el-table>
  </section>
  <el-dialog v-model="dialog" title="员工与权限" width="520px"
    ><el-form label-position="top"
      ><el-form-item label="账户"><el-input v-model="form.username" maxlength="32" /></el-form-item
      ><el-form-item label="姓名"><el-input v-model="form.name" maxlength="32" /></el-form-item
      ><el-form-item label="角色"
        ><el-select v-model="form.role"
          ><el-option
            v-for="role in platform
              ? ['PLATFORM_ADMIN', 'SHOP_MANAGER', 'OPERATOR', 'DELIVERER']
              : ['OPERATOR']"
            :key="role"
            :value="role"
            :label="roles[role]" /></el-select></el-form-item
      ><el-form-item
        v-if="platform && ['SHOP_MANAGER', 'OPERATOR'].includes(form.role)"
        label="归属店铺"
        ><el-select v-model="form.storeId"
          ><el-option
            v-for="s in stores"
            :key="s.id"
            :value="s.id"
            :label="s.campusName + ' / ' + s.name" /></el-select></el-form-item
      ><el-form-item v-if="form.role === 'DELIVERER'" label="配送校区"
        ><el-select v-model="form.campusId"
          ><el-option
            v-for="c in campuses"
            :key="c.id"
            :value="c.id"
            :label="c.name" /></el-select></el-form-item
      ><el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item
      ><el-form-item :label="editing ? '重置密码，留空不修改' : '初始密码，至少12个字符'"
        ><el-input v-model="form.password" type="password" show-password /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" @click="saveStaff">保存</el-button></template
    ></el-dialog
  ><el-dialog :model-value="!!imageUrl" title="售后举证" @close="imageUrl = ''"
    ><img :src="imageUrl" style="max-width: 100%"
  /></el-dialog>
</template>

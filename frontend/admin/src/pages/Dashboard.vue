<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { useSession } from '../stores/session';
import { selectedStore } from '../context';
import type { Campus, Store } from '../../../contracts/platform';
const emit = defineEmits(['stores-changed']),
  session = useSession(),
  campuses = ref<Campus[]>([]),
  stores = ref<Store[]>([]),
  platform = computed(() => session.session?.role === 'PLATFORM_ADMIN'),
  dialog = ref(false),
  editing = ref(''),
  busy = ref(false),
  campusName = ref('');
const form = ref({
    campusId: '',
    name: '',
    enabled: true,
    open: false,
    deliveryFeeCents: 6,
    packagingFeeCents: 1,
    hours: ['08:00-22:00'],
    phone: '',
  }),
  hours = ref('08:00-22:00');
async function load() {
  try {
    stores.value = await request<Store[]>('/merchant/stores');
    campuses.value = await request<Campus[]>(
      platform.value ? '/platform/campuses' : '/user/campuses',
    );
  } catch (e) {
    notifyError(e);
  }
}
async function addCampus() {
  try {
    await request('/platform/campuses', 'POST', { name: campusName.value, enabled: true });
    campusName.value = '';
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function toggle(c: Campus) {
  try {
    await request('/platform/campuses/' + c.id, 'PUT', { name: c.name, enabled: !c.enabled });
    await load();
    emit('stores-changed');
  } catch (e) {
    notifyError(e);
  }
}
function edit(s?: Store) {
  editing.value = s?.id || '';
  form.value = {
    campusId: s?.campusId || campuses.value[0]?.id || '',
    name: s?.name || '',
    enabled: s?.enabled ?? true,
    open: s?.open ?? false,
    deliveryFeeCents: Number(s?.deliveryFee ?? 6),
    packagingFeeCents: Number(s?.packagingFee ?? 1),
    hours: s?.hours || ['08:00-22:00'],
    phone: s?.phone || '',
  };
  hours.value = form.value.hours.join(',');
  dialog.value = true;
}
async function save() {
  busy.value = true;
  try {
    form.value.hours = hours.value
      .split(/[,，]/)
      .map((x) => x.trim())
      .filter(Boolean);
    await request(
      editing.value ? '/merchant/stores/' + editing.value : '/platform/stores',
      editing.value ? 'PUT' : 'POST',
      {
        ...form.value,
        deliveryFeeCents: Math.round(form.value.deliveryFeeCents * 100),
        packagingFeeCents: Math.round(form.value.packagingFeeCents * 100),
      },
    );
    dialog.value = false;
    await load();
    emit('stores-changed');
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="page-head">
    <div>
      <p class="eyebrow">CAMPUS NETWORK</p>
      <h1>校区与店铺</h1>
      <p class="muted">每家店独立经营，校区内共享配送。停用后仍可处理历史订单。</p>
    </div>
    <el-button v-if="platform" type="primary" @click="edit()">新增店铺</el-button>
  </section>
  <section v-if="platform" class="panel">
    <h2>校区管理</h2>
    <div class="toolbar">
      <el-input v-model="campusName" placeholder="新增校区名称" style="width: 240px" /><el-button
        @click="addCampus"
        :disabled="!campusName.trim()"
        >新增校区</el-button
      >
    </div>
    <div class="toolbar" v-for="c in campuses" :key="c.id">
      <strong>{{ c.name }}</strong
      ><el-tag>{{ c.enabled ? '启用' : '停用' }}</el-tag
      ><el-button text @click="toggle(c)">{{ c.enabled ? '停用' : '启用' }}</el-button>
    </div>
  </section>
  <section class="panel">
    <h2>店铺列表</h2>
    <el-table :data="stores"
      ><el-table-column prop="campusName" label="校区" /><el-table-column
        prop="name"
        label="店铺"
      /><el-table-column label="营业"
        ><template #default="{ row }"
          >{{ row.open ? '接单开关已开' : '暂停接单' }} ·
          {{ row.enabled ? '启用' : '停用' }}</template
        ></el-table-column
      ><el-table-column label="操作" width="210"
        ><template #default="{ row }"
          ><el-button text @click="selectedStore = row.id">进入店铺</el-button
          ><el-button v-if="session.session?.role !== 'OPERATOR'" text @click="edit(row)"
            >营业配置</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </section>
  <el-dialog v-model="dialog" :title="editing ? '店铺与营业规则' : '新增店铺'" width="560px"
    ><el-form label-position="top"
      ><el-form-item label="校区"
        ><el-select v-model="form.campusId" :disabled="!!editing"
          ><el-option
            v-for="c in campuses"
            :key="c.id"
            :value="c.id"
            :label="c.name" /></el-select></el-form-item
      ><el-form-item label="店铺名称"><el-input v-model="form.name" maxlength="80" /></el-form-item
      ><el-form-item v-if="platform" label="店铺启用"
        ><el-switch v-model="form.enabled" /></el-form-item
      ><el-form-item label="接收新订单"><el-switch v-model="form.open" /></el-form-item
      ><el-form-item label="营业时段，多个用逗号分隔"
        ><el-input v-model="hours" placeholder="08:00-12:00,17:00-22:00" /></el-form-item
      ><el-form-item label="每单配送费（元）"
        ><el-input-number
          v-model="form.deliveryFeeCents"
          :min="0"
          :max="100"
          :precision="2" /></el-form-item
      ><el-form-item label="每件包装费（元）"
        ><el-input-number
          v-model="form.packagingFeeCents"
          :min="0"
          :max="100"
          :precision="2" /></el-form-item
      ><el-form-item label="联系电话"
        ><el-input v-model="form.phone" maxlength="20" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="busy" @click="save">保存</el-button></template
    ></el-dialog
  >
</template>

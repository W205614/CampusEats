<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { api, toast, loggedIn, confirm } from '../../api';
import type { Campus, Address } from '../../../../contracts/platform';
const campuses = ref<Campus[]>([]),
  campus = ref(''),
  buildings = ref<{ id: string; name: string }[]>([]),
  addresses = ref<Address[]>([]),
  editing = ref<string | null>(null),
  dialog = ref(false),
  form = ref({ buildingId: '', room: '', consignee: '', phone: '' });
async function load() {
  if (!loggedIn()) return;
  try {
    [campuses.value, addresses.value] = await Promise.all([
      api<Campus[]>('/campuses'),
      api<Address[]>('/addresses'),
    ]);
    if (!campus.value) campus.value = campuses.value[0]?.id || '';
    await loadBuildings();
  } catch (e) {
    toast(e);
  }
}
async function loadBuildings() {
  try {
    buildings.value = await api<typeof buildings.value>('/campuses/' + campus.value + '/buildings');
  } catch (e) {
    toast(e);
  }
}
function edit(a?: Address) {
  editing.value = a?.id || null;
  if (a) {
    campus.value = a.campusId;
    form.value = { buildingId: a.buildingId, room: a.room, consignee: a.consignee, phone: a.phone };
  } else form.value = { buildingId: '', room: '', consignee: '', phone: '' };
  loadBuildings();
  dialog.value = true;
}
async function save() {
  try {
    await api(
      '/addresses' + (editing.value ? '/' + editing.value : ''),
      editing.value ? 'PUT' : 'POST',
      form.value,
    );
    dialog.value = false;
    await load();
  } catch (e) {
    toast(e);
  }
}
async function remove(a: Address) {
  if (!(await confirm('删除这个地址？历史订单地址不受影响。'))) return;
  try {
    await api('/addresses/' + a.id, 'DELETE');
    await load();
  } catch (e) {
    toast(e);
  }
}
onShow(load);
</script>
<template>
  <view class="screen"
    ><view class="title">校园收货地址</view
    ><view class="subtitle">每家店只能配送到所在校区；跨校区合单可逐店选择地址。</view
    ><button class="primary" @click="edit()">新增地址</button
    ><view class="card" v-for="a in addresses" :key="a.id"
      ><view
        >{{ campuses.find((c) => c.id === a.campusId)?.name || '历史校区' }} · {{ a.buildingName }}
        {{ a.room }}</view
      ><view>{{ a.consignee }} {{ a.phone }}</view
      ><view class="row"
        ><text class="tap" @click="edit(a)">编辑</text
        ><text class="tap" @click="remove(a)">删除</text></view
      ></view
    ><view class="modal" v-if="dialog" @click="dialog = false"
      ><view class="card" @click.stop
        ><view class="row"
          ><text class="section-title">{{ editing ? '编辑地址' : '新增地址' }}</text
          ><text class="tap" @click="dialog = false">关闭</text></view
        ><picker
          :range="campuses.map((c) => c.name)"
          :value="campuses.findIndex((c) => c.id === campus)"
          @change="
            campus = campuses[Number($event.detail.value)]!.id;
            form.buildingId = '';
            loadBuildings();
          "
          ><view class="field-label tap"
            >校区：{{ campuses.find((c) => c.id === campus)?.name }} ▾</view
          ></picker
        ><picker
          :range="buildings.map((b) => b.name)"
          :value="buildings.findIndex((b) => b.id === form.buildingId)"
          @change="form.buildingId = buildings[Number($event.detail.value)]!.id"
          ><view class="field-label tap"
            >楼栋：{{ buildings.find((b) => b.id === form.buildingId)?.name || '请选择' }} ▾</view
          ></picker
        ><input v-model="form.room" placeholder="房间号" maxlength="32" /><input
          v-model="form.consignee"
          placeholder="收货人"
          maxlength="32"
        /><input v-model="form.phone" placeholder="手机号码" maxlength="11" /><button
          class="primary"
          :disabled="!form.buildingId"
          @click="save"
        >
          保存地址
        </button></view
      ></view
    ></view
  >
</template>

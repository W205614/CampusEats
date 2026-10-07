<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { api, toast, loggedIn, confirm } from "../../api";
import type { Address, Building } from "../../../../contracts/models";
const rows = ref<Address[]>([]),
  buildings = ref<Building[]>([]),
  editing = ref(false),
  id = ref<string>(),
  busy = ref(false),
  buildingIndex = ref(0),
  form = ref({ room: "", consignee: "", phone: "" });
async function load() {
  if (!loggedIn()) return;
  try {
    [rows.value, buildings.value] = await Promise.all([
      api<Address[]>("/addresses"),
      api<Building[]>("/buildings"),
    ]);
  } catch (e) {
    toast(e);
  }
}
function edit(row?: Address) {
  id.value = row?.id;
  form.value = {
    room: row?.room || "",
    consignee: row?.consignee || "",
    phone: row?.phone || "",
  };
  buildingIndex.value = Math.max(
    0,
    buildings.value.findIndex((b) => b.id === row?.buildingId),
  );
  editing.value = true;
}
async function save() {
  if (!buildings.value[buildingIndex.value]) {
    toast(new Error("尚未配置配送楼栋"));
    return;
  }
  busy.value = true;
  try {
    await api(
      "/addresses" + (id.value ? "/" + id.value : ""),
      id.value ? "PUT" : "POST",
      { ...form.value, buildingId: buildings.value[buildingIndex.value]!.id },
    );
    editing.value = false;
    await load();
  } catch (e) {
    toast(e);
  } finally {
    busy.value = false;
  }
}
async function remove(row: Address) {
  if (!(await confirm("删除此地址？历史订单的配送地址仍会保留。"))) return;
  try {
    await api("/addresses/" + row.id, "DELETE");
    await load();
  } catch (e) {
    toast(e);
  }
}
async function makeDefault(row: Address) {
  try {
    await api("/addresses/" + row.id + "/default", "POST");
    await load();
  } catch (e) {
    toast(e);
  }
}
onShow(load);

const navTo = (url: string) => uni.navigateTo({ url });
const tabTo = (url: string) => uni.switchTab({ url });
</script>
<template>
  <view class="screen"
    ><view class="row" style="margin-bottom: 25rpx"
      ><view class="section-title" style="margin: 0">校园配送地址</view
      ><text class="tap" @click="edit()">新增地址</text></view
    ><view class="notice"
      >仅配送到已启用的校园楼栋，请填写准确房间或接收位置。</view
    ><view class="card" v-for="row in rows" :key="row.id"
      ><view class="row"
        ><view class="card-name"
          >{{ row.buildingName || "请选择校园楼栋" }} {{ row.room }}</view
        ><text v-if="row.isDefault" class="tag">默认</text></view
      ><view class="muted" style="margin: 15rpx 0"
        >{{ row.consignee }}　{{ row.phone }}</view
      ><view class="row"
        ><text class="tap" @click="makeDefault(row)">设为默认</text
        ><view
          ><text class="tap" @click="edit(row)">编辑　</text
          ><text class="tap" @click="remove(row)">删除</text></view
        ></view
      ></view
    ><view class="empty" v-if="!rows.length && !editing"
      ><view class="empty-title">添加一个校园地址</view
      ><view>方便确认配送范围和接收位置。</view></view
    ><view class="card" v-if="editing"
      ><view class="section-title" style="margin: 0">{{
        id ? "编辑地址" : "新增地址"
      }}</view
      ><view class="field-label">校园楼栋</view
      ><picker
        :range="buildings.map((b) => b.name)"
        :value="buildingIndex"
        @change="buildingIndex = Number($event.detail.value)"
        ><view class="field">{{
          buildings[buildingIndex]?.name || "暂无配送楼栋"
        }}</view></picker
      ><view class="field-label">房间 / 接收位置</view
      ><input
        class="field"
        v-model="form.room"
        placeholder="例如：305 或 一楼服务台"
        maxlength="32"
      /><view class="field-label">收货人</view
      ><input
        class="field"
        v-model="form.consignee"
        placeholder="姓名"
        maxlength="32"
      /><view class="field-label">手机号</view
      ><input
        class="field"
        v-model="form.phone"
        type="number"
        maxlength="11"
        placeholder="11位手机号"
      /><view class="form-actions"
        ><button class="secondary" @click="editing = false">取消</button
        ><button class="primary" :loading="busy" :disabled="busy" @click="save">
          保存地址
        </button></view
      ></view
    ></view
  >
</template>

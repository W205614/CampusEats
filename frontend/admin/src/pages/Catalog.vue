<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { api, body, notifyError } from "../api";
import {
  ElMessage,
  ElMessageBox,
  type UploadRequestOptions,
} from "element-plus";
import type { Product, Category } from "../../../contracts/models";
const kind = String(useRoute().params.kind);
const rows = ref<any[]>([]),
  categories = ref<Category[]>([]),
  dishes = ref<Product[]>([]),
  dialog = ref(false),
  saving = ref(false),
  id = ref<string>();
const form = ref<any>({});
const title = computed(() =>
  kind === "CATEGORY" ? "分类管理" : kind === "DISH" ? "菜品管理" : "套餐管理",
);
async function load() {
  try {
    categories.value = await api("/categories");
    rows.value =
      kind === "CATEGORY" ? categories.value : await api("/products/" + kind);
    if (kind === "SETMEAL") dishes.value = await api("/products/DISH");
  } catch (e) {
    notifyError(e);
  }
}
function edit(row?: any) {
  id.value = row?.id;
  form.value =
    kind === "CATEGORY"
      ? {
          name: row?.name || "",
          type: row?.type || 1,
          sort: row?.sort || 0,
          enabled: row ? row.status === 1 : true,
        }
      : {
          name: row?.name || "",
          categoryId: row?.categoryId || "",
          price: Number(row?.price || 0),
          enabled: row ? row.status === 1 : true,
          description: row?.description || "",
          image: row?.image || "",
          flavors: (row?.flavors || []).map((f: any) => ({
            name: f.name,
            values: f.values.join("、"),
          })),
          components: (row?.components || []).map((c: any) => ({
            dishId: c.dishId,
            copies: c.copies,
          })),
        };
  dialog.value = true;
}
async function save() {
  saving.value = true;
  try {
    const input =
      kind === "CATEGORY"
        ? form.value
        : {
            ...form.value,
            categoryId: form.value.categoryId,
            flavors: form.value.flavors.map((f: any) => ({
              name: f.name,
              values: f.values
                .split(/[、,，]/)
                .map((s: string) => s.trim())
                .filter(Boolean),
            })),
            components: form.value.components.map((c: any) => ({
              dishId: c.dishId,
              copies: c.copies,
            })),
          };
    await api(
      (kind === "CATEGORY" ? "/categories" : "/products/" + kind) +
        (id.value ? "/" + id.value : ""),
      { method: id.value ? "PUT" : "POST", body: body(input) },
    );
    dialog.value = false;
    ElMessage.success("保存成功");
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    saving.value = false;
  }
}
async function remove(row: any) {
  try {
    await ElMessageBox.confirm(
      "确认删除“" + row.name + "”？已下单的历史快照会保留。",
      "删除确认",
      { type: "warning" },
    );
    await api(
      (kind === "CATEGORY" ? "/categories" : "/products/" + kind) +
        "/" +
        row.id,
      { method: "DELETE" },
    );
    await load();
  } catch (e) {
    if (e !== "cancel" && e !== "close") notifyError(e);
  }
}
async function upload(option: UploadRequestOptions) {
  const input = new FormData();
  input.append("file", option.file);
  try {
    const result = await api<{ url: string }>("/uploads", {
      method: "POST",
      body: input,
    });
    form.value.image = result.url;
    option.onSuccess(result);
  } catch (e) {
    notifyError(e);
  }
}
onMounted(load);
</script>
<template>
  <div class="page-heading">
    <div>
      <h2>{{ title }}</h2>
      <p class="muted">停售菜品会同步停售关联套餐，历史订单保留原始价格。</p>
    </div>
    <el-button type="primary" @click="edit()"
      >新增{{
        kind === "CATEGORY" ? "分类" : kind === "DISH" ? "菜品" : "套餐"
      }}</el-button
    >
  </div>
  <section class="panel">
    <el-table :data="rows"
      ><el-table-column v-if="kind !== 'CATEGORY'" label="图片" width="70"
        ><template #default="{ row }"
          ><img
            class="picture"
            :src="row.image || '/demo-images/dish.svg'"
            alt="餐品" /></template></el-table-column
      ><el-table-column
        prop="name"
        label="名称"
        min-width="180"
      /><el-table-column v-if="kind !== 'CATEGORY'" label="售价" width="120"
        ><template #default="{ row }"
          >¥{{ row.price }}</template
        ></el-table-column
      ><el-table-column v-else label="类型" width="120"
        ><template #default="{ row }">{{
          row.type === 1 ? "菜品分类" : "套餐分类"
        }}</template></el-table-column
      ><el-table-column
        v-if="kind === 'DISH'"
        prop="defaultQuota"
        label="默认每日份数"
        width="140"
      /><el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
            row.status === 1 ? "启用" : "停用"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="操作" width="150"
        ><template #default="{ row }"
          ><el-button text type="primary" @click="edit(row)">编辑</el-button
          ><el-button text type="danger" @click="remove(row)"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </section>
  <el-dialog v-model="dialog" :title="id ? '编辑' : '新增'" width="620px"
    ><el-form label-position="top" @submit.prevent="save"
      ><div class="two-columns">
        <el-form-item label="名称"
          ><el-input v-model="form.name" maxlength="32" /></el-form-item
        ><el-form-item label="启用状态"
          ><el-switch v-model="form.enabled"
        /></el-form-item>
      </div>
      <template v-if="kind === 'CATEGORY'"
        ><el-form-item label="分类类型"
          ><el-select v-model="form.type"
            ><el-option label="菜品分类" :value="1" /><el-option
              label="套餐分类"
              :value="2" /></el-select></el-form-item
        ><el-form-item label="排序"
          ><el-input-number
            v-model="form.sort"
            :min="0" /></el-form-item></template
      ><template v-else
        ><div class="two-columns">
          <el-form-item label="所属分类"
            ><el-select v-model="form.categoryId" placeholder="请选择"
              ><el-option
                v-for="c in categories.filter(
                  (x) => x.type === (kind === 'DISH' ? 1 : 2),
                )"
                :key="c.id"
                :label="c.name"
                :value="c.id" /></el-select></el-form-item
          ><el-form-item label="售价（元）"
            ><el-input-number
              v-model="form.price"
              :min="0.01"
              :max="99999.99"
              :precision="2"
          /></el-form-item>
        </div>
        <el-form-item label="餐品介绍"
          ><el-input
            v-model="form.description"
            type="textarea"
            maxlength="255" /></el-form-item
        ><el-form-item label="图片"
          ><img
            v-if="form.image"
            :src="form.image"
            class="picture"
            alt="已上传图片"
          /><el-upload
            :http-request="upload"
            :show-file-list="false"
            accept="image/png,image/jpeg"
            ><el-button>上传 JPEG / PNG</el-button
            ><template #tip
              ><span class="muted small"
                >最大5MB，边长不超过4096像素</span
              ></template
            ></el-upload
          ></el-form-item
        ><template v-if="kind === 'DISH'"
          ><h3>口味选项</h3>
          <div class="form-row" v-for="(f, i) in form.flavors" :key="i">
            <el-input v-model="f.name" placeholder="例如：辣度" /><el-input
              v-model="f.values"
              placeholder="不辣、微辣、中辣"
            /><el-button text @click="form.flavors.splice(i, 1)"
              >移除</el-button
            >
          </div>
          <el-button @click="form.flavors.push({ name: '', values: '' })"
            >添加口味组</el-button
          ></template
        ><template v-else
          ><h3>套餐组成</h3>
          <div class="form-row" v-for="(c, i) in form.components" :key="i">
            <el-select v-model="c.dishId" placeholder="选择菜品" style="flex: 1"
              ><el-option
                v-for="d in dishes"
                :key="d.id"
                :value="d.id"
                :label="d.name" /></el-select
            ><el-input-number v-model="c.copies" :min="1" :max="50" /><el-button
              text
              @click="form.components.splice(i, 1)"
              >移除</el-button
            >
          </div>
          <el-button @click="form.components.push({ dishId: '', copies: 1 })"
            >添加菜品</el-button
          ></template
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

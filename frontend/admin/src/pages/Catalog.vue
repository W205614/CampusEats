<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { selectedStore } from '../context';
import { localDate } from '../dates';
import type { Product } from '../../../contracts/platform';
interface Category {
  id: string;
  name: string;
  type: number;
  sort: number;
  status: number;
}
const products = ref<Product[]>([]),
  categories = ref<Category[]>([]),
  dialog = ref(false),
  id = ref(''),
  kind = ref<'DISH' | 'SETMEAL'>('DISH'),
  categoryName = ref(''),
  categoryType = ref(1),
  saving = ref(false),
  date = ref(localDate()),
  quotaProduct = ref(''),
  quotaTotal = ref(100),
  quotaDefault = ref(false),
  quotas = ref<
    {
      dishId: string;
      name: string;
      total: number;
      reserved: number;
      consumed: number;
      defaultQuota: number;
    }[]
  >([]);
const form = ref({
  name: '',
  categoryId: '',
  price: 12,
  enabled: true,
  description: '',
  image: '',
  flavors: [] as { name: string; values: string[] }[],
  components: [] as { dishId: string; copies: number }[],
});
const flavorName = ref(''),
  flavorValues = ref('');
const base = () => '/merchant/stores/' + selectedStore.value;
async function load() {
  if (!selectedStore.value) return;
  try {
    [products.value, categories.value, quotas.value] = await Promise.all([
      request<Product[]>(base() + '/products'),
      request<Category[]>(base() + '/categories'),
      request<typeof quotas.value>(base() + '/quotas?date=' + date.value),
    ]);
  } catch (e) {
    notifyError(e);
  }
}
function edit(product?: Product) {
  id.value = product?.id || '';
  kind.value = product?.itemType || 'DISH';
  form.value = {
    name: product?.name || '',
    categoryId: product?.categoryId || '',
    price: Number(product?.price || 12),
    enabled: product ? product.status === 1 : true,
    description: product?.description || '',
    image: product?.image || '',
    flavors: (product?.flavors || []).map((f) => ({ name: f.name, values: [...f.values] })),
    components: (product?.components || []).map((c) => ({ dishId: c.dishId, copies: c.copies })),
  };
  dialog.value = true;
}
async function save() {
  saving.value = true;
  try {
    await request(
      base() + '/products/' + kind.value + (id.value ? '/' + id.value : ''),
      id.value ? 'PUT' : 'POST',
      form.value,
    );
    dialog.value = false;
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    saving.value = false;
  }
}
async function addCategory() {
  try {
    await request(base() + '/categories', 'POST', {
      name: categoryName.value,
      type: categoryType.value,
      sort: 0,
      enabled: true,
    });
    categoryName.value = '';
    await load();
  } catch (e) {
    notifyError(e);
  }
}
async function toggle(c: Category) {
  try {
    await request(base() + '/categories/' + c.id, 'PUT', {
      name: c.name,
      type: c.type,
      sort: c.sort,
      enabled: c.status !== 1,
    });
    await load();
  } catch (e) {
    notifyError(e);
  }
}
function addFlavor() {
  if (flavorName.value && flavorValues.value) {
    form.value.flavors.push({
      name: flavorName.value,
      values: flavorValues.value
        .split(/[,，]/)
        .map((x) => x.trim())
        .filter(Boolean),
    });
    flavorName.value = '';
    flavorValues.value = '';
  }
}
async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    const data = new FormData();
    data.append('file', file);
    form.value.image = (await request<{ url: string }>(base() + '/uploads', 'POST', data)).url;
  } catch (e) {
    notifyError(e);
  }
}
async function saveQuota() {
  try {
    await request(base() + '/quotas?date=' + date.value, 'PUT', {
      dishId: quotaProduct.value,
      total: quotaTotal.value,
      defaultQuota: quotaDefault.value,
    });
    await load();
  } catch (e) {
    notifyError(e);
  }
}
onMounted(load);
</script>
<template>
  <section class="page-head">
    <div>
      <h1>商品与每日供餐</h1>
      <p class="muted">当前店铺独立维护分类、口味、套餐和配额。</p>
    </div>
    <el-button type="primary" :disabled="!selectedStore" @click="edit()">新增商品</el-button>
  </section>
  <section class="panel">
    <h2>分类</h2>
    <div class="toolbar">
      <el-input v-model="categoryName" placeholder="分类名称" style="width: 200px" /><el-select
        v-model="categoryType"
        style="width: 120px"
        ><el-option :value="1" label="菜品" /><el-option :value="2" label="套餐" /></el-select
      ><el-button :disabled="!categoryName" @click="addCategory">新增分类</el-button>
    </div>
    <div class="toolbar">
      <el-button v-for="c in categories" :key="c.id" @click="toggle(c)"
        >{{ c.name }} · {{ c.status === 1 ? '启用' : '停用' }}</el-button
      >
    </div>
  </section>
  <section class="panel">
    <el-table :data="products"
      ><el-table-column prop="name" label="商品" /><el-table-column label="类型"
        ><template #default="{ row }">{{
          row.itemType === 'DISH' ? '菜品' : '套餐'
        }}</template></el-table-column
      ><el-table-column prop="price" label="售价 / 元" /><el-table-column
        prop="remaining"
        label="今日剩余"
      /><el-table-column label="状态"
        ><template #default="{ row }">{{
          row.status === 1 ? '起售' : '停售'
        }}</template></el-table-column
      ><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button text @click="edit(row)">编辑</el-button></template
        ></el-table-column
      ></el-table
    >
  </section>
  <section class="panel">
    <h2>每日配额</h2>
    <div class="toolbar">
      <input v-model="date" type="date" @change="load" /><el-select
        v-model="quotaProduct"
        placeholder="菜品"
        ><el-option
          v-for="d in products.filter((x) => x.itemType === 'DISH')"
          :key="d.id"
          :value="d.id"
          :label="d.name" /></el-select
      ><el-input-number v-model="quotaTotal" :min="0" :max="100000" /><el-switch
        v-model="quotaDefault"
        active-text="修改默认份数"
      /><el-button :disabled="!quotaProduct" @click="saveQuota">保存配额</el-button>
    </div>
    <el-table :data="quotas"
      ><el-table-column prop="name" label="菜品" /><el-table-column
        prop="total"
        label="当日总量" /><el-table-column prop="reserved" label="预占" /><el-table-column
        prop="consumed"
        label="已消耗" /><el-table-column prop="defaultQuota" label="默认份数"
    /></el-table>
  </section>
  <el-dialog v-model="dialog" :title="id ? '编辑商品' : '新增商品'" width="640px"
    ><el-form label-position="top"
      ><el-form-item label="类型"
        ><el-select v-model="kind" :disabled="!!id"
          ><el-option value="DISH" label="菜品" /><el-option
            value="SETMEAL"
            label="套餐" /></el-select></el-form-item
      ><el-form-item label="名称"><el-input v-model="form.name" maxlength="32" /></el-form-item
      ><el-form-item label="分类"
        ><el-select v-model="form.categoryId"
          ><el-option
            v-for="c in categories.filter((c) => c.type === (kind === 'DISH' ? 1 : 2))"
            :key="c.id"
            :value="c.id"
            :label="c.name" /></el-select></el-form-item
      ><el-form-item label="售价 / 元"
        ><el-input-number v-model="form.price" :min="0.01" :precision="2" /></el-form-item
      ><el-form-item label="起售"><el-switch v-model="form.enabled" /></el-form-item
      ><el-form-item label="介绍"
        ><el-input v-model="form.description" maxlength="255" /></el-form-item
      ><el-form-item label="商品图片"
        ><input type="file" accept="image/png,image/jpeg" @change="upload" /><img
          v-if="form.image"
          :src="form.image"
          width="80" /></el-form-item
      ><template v-if="kind === 'DISH'"
        ><div class="toolbar">
          <el-input v-model="flavorName" placeholder="口味名称" /><el-input
            v-model="flavorValues"
            placeholder="选项，用逗号分隔"
          /><el-button @click="addFlavor">加入口味</el-button>
        </div>
        <div class="toolbar" v-for="(f, i) in form.flavors" :key="i">
          {{ f.name }}：{{ f.values.join('、') }}
          <el-button text @click="form.flavors.splice(i, 1)">移除</el-button>
        </div></template
      ><template v-else
        ><div class="toolbar" v-for="(c, i) in form.components" :key="i">
          <el-select v-model="c.dishId" placeholder="本店菜品"
            ><el-option
              v-for="d in products.filter((d) => d.itemType === 'DISH')"
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
          >添加套餐菜品</el-button
        ></template
      ></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
    ></el-dialog
  >
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { selectedStore } from '../context';
import { useSession } from '../stores/session';
import { cents, type Coupon, type Product } from '../../../contracts/platform';
const session = useSession(),
  platform = computed(() => session.session?.role === 'PLATFORM_ADMIN'),
  level = ref('STORE'),
  coupons = ref<Coupon[]>([]),
  products = ref<Product[]>([]),
  promotions = ref<
    {
      id: string;
      itemType: string;
      itemId: string;
      priceCents: string;
      startsAt: string;
      endsAt: string;
      enabled: boolean;
    }[]
  >([]),
  dialog = ref(false),
  mode = ref('coupon'),
  editing = ref(''),
  busy = ref(false);
const now = new Date(),
  local = (d: Date) =>
    new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
const coupon = ref({
  name: '',
  kind: 'REDUCTION',
  threshold: 20,
  value: 3,
  cap: 0,
  total: 100,
  perUser: 1,
  startsAt: local(now),
  endsAt: local(new Date(now.getTime() + 7 * 86400000)),
  enabled: true,
});
const productKey = ref('');
function chooseProduct(key: string) {
  const [type, id] = key.split(':');
  promotion.value.itemType = type!;
  promotion.value.itemId = id!;
}
const promotion = ref({
  itemType: 'DISH',
  itemId: '',
  price: 10,
  startsAt: local(now),
  endsAt: local(new Date(now.getTime() + 86400000)),
  enabled: true,
});
const base = () =>
  level.value === 'PLATFORM'
    ? '/platform/coupons'
    : '/merchant/stores/' + selectedStore.value + '/coupons';
async function load() {
  try {
    coupons.value = await request<Coupon[]>(base());
    if (selectedStore.value)
      [products.value, promotions.value] = await Promise.all([
        request<Product[]>('/merchant/stores/' + selectedStore.value + '/products'),
        request<typeof promotions.value>('/merchant/stores/' + selectedStore.value + '/promotions'),
      ]);
  } catch (e) {
    notifyError(e);
  }
}
function create(kind: string) {
  mode.value = kind;
  editing.value = '';
  dialog.value = true;
}
function editCoupon(c: Coupon) {
  editing.value = c.id;
  mode.value = 'coupon';
  coupon.value = {
    name: c.name,
    kind: c.kind,
    threshold: Number(c.thresholdCents) / 100,
    value: Number(c.value) / (c.kind === 'REDUCTION' ? 100 : 1000),
    cap: Number(c.capCents || 0) / 100,
    total: c.total,
    perUser: c.perUser,
    startsAt: c.startsAt.slice(0, 16),
    endsAt: c.endsAt.slice(0, 16),
    enabled: c.enabled,
  };
  dialog.value = true;
}
function editPromotion(p: (typeof promotions.value)[number]) {
  productKey.value = p.itemType + ':' + p.itemId;
  editing.value = p.id;
  mode.value = 'promotion';
  promotion.value = {
    ...p,
    price: Number(p.priceCents) / 100,
    startsAt: p.startsAt.slice(0, 16),
    endsAt: p.endsAt.slice(0, 16),
  };
  dialog.value = true;
}
async function save() {
  busy.value = true;
  try {
    if (mode.value === 'coupon') {
      const c = coupon.value;
      await request(
        base() + (editing.value ? '/' + editing.value : ''),
        editing.value ? 'PUT' : 'POST',
        {
          name: c.name,
          kind: c.kind,
          thresholdCents: Math.round(c.threshold * 100),
          value: Math.round(c.value * (c.kind === 'REDUCTION' ? 100 : 1000)),
          capCents: c.cap ? Math.round(c.cap * 100) : null,
          total: c.total,
          perUser: c.perUser,
          startsAt: c.startsAt + ':00',
          endsAt: c.endsAt + ':00',
          enabled: c.enabled,
        },
      );
    } else
      await request(
        '/merchant/stores/' +
          selectedStore.value +
          '/promotions' +
          (editing.value ? '/' + editing.value : ''),
        editing.value ? 'PUT' : 'POST',
        {
          ...promotion.value,
          priceCents: Math.round(promotion.value.price * 100),
          startsAt: promotion.value.startsAt + ':00',
          endsAt: promotion.value.endsAt + ':00',
        },
      );
    dialog.value = false;
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}
async function stop(c: Coupon) {
  try {
    await request(base() + '/' + c.id + '/stop', 'POST');
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
      <h1>营销管理</h1>
      <p class="muted">先活动价，再店铺券，最后平台券。优惠只抵商品金额，支付后不返券。</p>
    </div>
    <el-button type="primary" @click="create('coupon')">新增优惠券</el-button>
  </section>
  <section class="panel">
    <div class="toolbar">
      <h2>优惠券</h2>
      <el-select v-if="platform" v-model="level" @change="load" style="width: 160px"
        ><el-option value="STORE" label="当前店铺券" /><el-option value="PLATFORM" label="平台券"
      /></el-select>
    </div>
    <el-table :data="coupons"
      ><el-table-column prop="name" label="名称" /><el-table-column label="规则"
        ><template #default="{ row }"
          >满{{ cents(row.thresholdCents) }}元，{{
            row.kind === 'REDUCTION'
              ? '减' + cents(row.value) + '元'
              : Number(row.value) / 1000 + '折'
          }}</template
        ></el-table-column
      ><el-table-column label="领取"
        ><template #default="{ row }"
          >{{ row.claimed }} / {{ row.total }}</template
        ></el-table-column
      ><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button v-if="!row.claimed" text @click="editCoupon(row)">编辑规则</el-button
          ><el-button text :disabled="!row.enabled" @click="stop(row)">{{
            row.enabled ? '停发' : '已停发'
          }}</el-button></template
        ></el-table-column
      ></el-table
    >
  </section>
  <section class="panel">
    <div class="toolbar">
      <h2>商品活动价</h2>
      <el-button @click="create('promotion')">新增活动</el-button>
    </div>
    <el-table :data="promotions"
      ><el-table-column label="商品"
        ><template #default="{ row }">{{
          products.find((p) => p.id === row.itemId && p.itemType === row.itemType)?.name ||
          row.itemId
        }}</template></el-table-column
      ><el-table-column label="活动价"
        ><template #default="{ row }">¥{{ cents(row.priceCents) }}</template></el-table-column
      ><el-table-column prop="startsAt" label="开始" /><el-table-column
        prop="endsAt"
        label="结束"
      /><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button text @click="editPromotion(row)">编辑 / 停用</el-button></template
        ></el-table-column
      ></el-table
    >
  </section>
  <el-dialog
    v-model="dialog"
    :title="mode === 'coupon' ? '优惠券规则' : '限时商品活动价'"
    width="560px"
    ><el-form label-position="top"
      ><template v-if="mode === 'coupon'"
        ><el-form-item label="名称"><el-input v-model="coupon.name" maxlength="80" /></el-form-item
        ><el-form-item label="优惠类型"
          ><el-select v-model="coupon.kind"
            ><el-option value="REDUCTION" label="满减" /><el-option
              value="DISCOUNT"
              label="折扣" /></el-select></el-form-item
        ><el-form-item label="使用门槛 / 元"
          ><el-input-number v-model="coupon.threshold" :min="0" :precision="2" /></el-form-item
        ><el-form-item
          :label="coupon.kind === 'REDUCTION' ? '减免金额 / 元' : '折扣，例如8代表八折'"
          ><el-input-number v-model="coupon.value" :min="0.01" :precision="2" /></el-form-item
        ><el-form-item label="优惠上限 / 元，0表示不限制"
          ><el-input-number v-model="coupon.cap" :min="0" :precision="2" /></el-form-item
        ><el-form-item label="发行数量"
          ><el-input-number v-model="coupon.total" :min="1" /></el-form-item
        ><el-form-item label="每人最多领取"
          ><el-input-number v-model="coupon.perUser" :min="1" :max="100" /></el-form-item
        ><el-form-item label="有效期"
          ><input type="datetime-local" v-model="coupon.startsAt" /> 至
          <input type="datetime-local" v-model="coupon.endsAt" /></el-form-item></template
      ><template v-else
        ><el-form-item label="商品"
          ><el-select v-model="productKey" :disabled="!!editing" @change="chooseProduct"
            ><el-option
              v-for="p in products"
              :key="p.itemType + p.id"
              :value="p.itemType + ':' + p.id"
              :label="p.name + ' (' + p.itemType + ')'" /></el-select></el-form-item
        ><el-form-item label="活动价 / 元"
          ><el-input-number v-model="promotion.price" :min="0.01" :precision="2" /></el-form-item
        ><el-form-item label="活动时间"
          ><input type="datetime-local" v-model="promotion.startsAt" /> 至
          <input type="datetime-local" v-model="promotion.endsAt" /></el-form-item
        ><el-form-item label="启用"
          ><el-switch v-model="promotion.enabled" /></el-form-item></template></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="busy" @click="save">保存</el-button></template
    ></el-dialog
  >
</template>

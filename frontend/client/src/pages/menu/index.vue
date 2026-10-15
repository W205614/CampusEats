<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { api, toast, loggedIn, image } from '../../api';
import { useSession } from '../../stores/session';
import {
  cents,
  type Campus,
  type Store,
  type Product,
  type Cart,
} from '../../../../contracts/platform';
const session = useSession(),
  campuses = ref<Campus[]>([]),
  stores = ref<Store[]>([]),
  campus = ref(''),
  store = ref<Store>(),
  products = ref<(Product & { storeName?: string })[]>([]),
  categories = ref<{ id: string; name: string }[]>([]),
  category = ref(''),
  openFilter = ref(0),
  campusSearch = ref(false),
  matchedStores = ref<Store[]>([]),
  query = ref(''),
  chosen = ref<Product>(),
  flavors = ref<Record<string, string>>({}),
  cart = ref<Cart>(),
  busy = ref(false),
  reviews = ref<{ rating: number; content: string; reply?: string }[]>([]),
  showReviews = ref(false);
let menuSequence = 0;
async function load() {
  try {
    campuses.value = await api<Campus[]>('/campuses');
    campus.value = String(uni.getStorageSync('campus-choice') || campus.value || campuses.value[0]?.id || '');
    if (!campuses.value.some((c) => c.id === campus.value))
      campus.value = campuses.value[0]?.id || '';
    await loadStores();
  } catch (e) {
    toast(e);
  }
}
async function loadStores() {
  try {
    uni.setStorageSync('campus-choice', campus.value);
    const targetCampus = campus.value;
    const result = await api<Store[]>('/stores?campusId=' + targetCampus);
    if (campus.value !== targetCampus) return;
    stores.value = result;
    const previous = String(uni.getStorageSync('store-choice') || '');
    store.value =
      stores.value.find((s) => s.id === (previous || store.value?.id)) || stores.value[0];
    await menu();
  } catch (e) {
    toast(e);
  }
}
async function menu() {
  campusSearch.value = false;
  matchedStores.value = [];
  const target = store.value;
  if (!target) {
    products.value = [];
    cart.value = undefined;
    return;
  }
  const sequence = ++menuSequence;
  products.value = [];
  cart.value = undefined;
  chosen.value = undefined;
  uni.setStorageSync('store-choice', target.id);
  try {
    const params = 'query=' + encodeURIComponent(query.value) + (category.value ? '&categoryId=' + category.value : '');
    const [items, currentCart, groups] = await Promise.all([
      api<Product[]>(
        '/stores/' +
          target.id +
          '/products' +
          '?' + params,
      ),
      session.session ? api<Cart>('/stores/' + target.id + '/cart') : Promise.resolve(undefined),
      api<typeof categories.value>('/stores/' + target.id + '/categories'),
    ]);
    if (sequence !== menuSequence || store.value?.id !== target.id) return;
    products.value = items;
    cart.value = currentCart;
    categories.value = groups;
  } catch (e) {
    if (sequence === menuSequence) toast(e);
  }
}

function campusChanged(e: { detail: { value: unknown } }) {
  campus.value = campuses.value[Number(e.detail.value)]!.id;
  store.value = undefined;
  query.value = '';
  category.value = '';
  loadStores();
}
function storeChanged(e: { detail: { value: unknown } }) {
  store.value = stores.value[Number(e.detail.value)];
  query.value = '';
  category.value = '';
  menu();
}
function pick(p: Product) {
  if (!loggedIn()) return;
  if (!store.value?.accepting) {
    toast(new Error('当前店铺不在营业时间'));
    return;
  }
  chosen.value = p;
  flavors.value = {};
  for (const f of p.flavors || []) flavors.value[f.name] = f.values[0] || '';
}
async function add() {
  if (!store.value || !chosen.value || !cart.value || busy.value) return;
  busy.value = true;
  try {
    cart.value = await api<Cart>('/stores/' + store.value.id + '/cart/items', 'POST', {
      cartVersion: cart.value.cartVersion,
      itemType: chosen.value.itemType,
      itemId: chosen.value.id,
      flavors: flavors.value,
      delta: 1,
    });
    chosen.value = undefined;
    uni.showToast({ title: '已加入该店购物车', icon: 'none' });
  } catch (e) {
    toast(e);
    await menu();
  } finally {
    busy.value = false;
  }
}
async function favorite(type: string, id: string, storeId = store.value?.id) {
  if (!loggedIn() || !store.value) return;
  try {
    await api('/stores/' + storeId + '/favorites/' + type + '/' + id, 'POST');
    uni.showToast({ title: '已收藏', icon: 'success' });
  } catch (e) {
    toast(e);
  }
}
async function searchCampus() {
  const sequence = ++menuSequence;
  campusSearch.value = true;
  try {
    const params = 'campusId=' + campus.value + '&query=' + encodeURIComponent(query.value) + (openFilter.value ? '&open=' + (openFilter.value === 1) : '');
    const [results, shops] = await Promise.all([
      api<typeof products.value>('/search?' + params),
      api<Store[]>('/stores?campusId=' + campus.value + '&query=' + encodeURIComponent(query.value)),
    ]);
    if (sequence === menuSequence) {
      products.value = results;
      matchedStores.value = shops.filter(s => !openFilter.value || s.accepting === (openFilter.value === 1));
    }
  } catch (e) {
    if (sequence === menuSequence) toast(e);
  }
}
async function openReviews() {
  if (!store.value) return;
  try {
    reviews.value = (
      await api<{ records: typeof reviews.value }>('/stores/' + store.value.id + '/reviews')
    ).records;
    showReviews.value = true;
  } catch (e) {
    toast(e);
  }
}
function searchPick(p: Product) {
  if (p.storeId !== store.value?.id) {
    store.value = stores.value.find((s) => s.id === p.storeId);
    category.value = '';
    menu().then(() => pick(p));
  } else pick(p);
}
function visitStore(s: Store) {
  store.value = s;
  category.value = '';
  query.value = '';
  menu();
}
const nav = (url: string) => uni.navigateTo({ url });
onShow(load);
</script>
<template>
  <view class="screen"
    ><view class="row"
      ><view class="eyebrow">CAMPUS EATS</view
      ><text
        class="tap"
        @click="nav(session.session ? '/pages/account/index' : '/pages/login/index')"
        >{{ session.session?.name || '登录' }}</text
      ></view
    ><view class="hero"
      ><view class="title">校园好店，<br />一起下单。</view
      ><view class="subtitle">分店备餐，按校区配送，也可到店自取。</view></view
    ><view class="card"
      ><picker
        :range="campuses.map((c) => c.name)"
        :value="campuses.findIndex((c) => c.id === campus)"
        @change="campusChanged"
        ><view class="row"
          ><text>校区</text
          ><text class="tap"
            >{{ campuses.find((c) => c.id === campus)?.name || '请选择' }} ▾</text
          ></view
        ></picker
      ><picker
        style="margin-top: 24rpx"
        :range="stores.map((s) => s.name)"
        :value="stores.findIndex((s) => s.id === store?.id)"
        @change="storeChanged"
        ><view class="row"
          ><text>店铺</text><text class="tap">{{ store?.name || '暂无店铺' }} ▾</text></view
        ></picker
      ></view
    ><view class="card"
      ><input v-model="query" placeholder="搜索餐品或店铺名称" @confirm="menu" />
      <picker :range="['本店全部分类', ...categories.map((c) => c.name)]" :value="Math.max(0, categories.findIndex((c) => c.id === category) + 1)" @change="category = categories[Number($event.detail.value) - 1]?.id || ''; menu()"><view class="field-label tap">分类：{{ categories.find((c) => c.id === category)?.name || '本店全部分类' }} ▾</view></picker>
      <picker :range="['本校区全部店铺', '仅营业中', '仅休息中']" :value="openFilter" @change="openFilter = Number($event.detail.value); searchCampus()"><view class="field-label tap">校区筛选：{{ ['本校区全部店铺', '仅营业中', '仅休息中'][openFilter] }} ▾</view></picker><view
        class="row"
        style="margin-top: 20rpx"
        ><button class="secondary small-button" @click="menu">搜本店</button
        ><button class="secondary small-button" @click="searchCampus">搜本校区</button></view
      ></view
    ><view v-if="store" class="row" style="margin-bottom: 24rpx"
      ><view
        ><text class="tag">{{ store.accepting ? '营业中' : '休息中' }}</text
        ><text class="muted"> {{ store.hours?.join(' / ') }}</text></view
      ><text class="tap" @click="favorite('STORE', store.id)">收藏店铺</text></view
    ><view class="row" v-if="store" style="margin-bottom: 24rpx"
      ><text class="tap" @click="nav('/pages/account/index')">领券与我的收藏</text
      ><text class="tap" @click="openReviews">店铺评价</text></view
    ><template v-if="campusSearch"><view class="section-title">店铺搜索结果</view><view class="card row" v-for="s in matchedStores" :key="s.id"><view>{{ s.name }}<view class="muted">{{ s.accepting ? '营业中' : '休息中' }} · {{ s.hours.join(' / ') }}</view></view><text class="tap" @click="visitStore(s)">进入店铺</text></view><view v-if="!matchedStores.length" class="muted">没有匹配的店铺。</view><view class="section-title">餐品搜索结果</view></template
    ><view class="card" v-for="p in products" :key="p.storeId + p.itemType + p.id"
      ><view class="row"
        ><image
          v-if="p.image"
          :src="image(p.image)"
          style="width: 130rpx; height: 130rpx; border-radius: 18rpx"
        /><view style="flex: 1; margin-left: 20rpx"
          ><view class="card-name">{{ p.name }}</view
          ><view v-if="campusSearch" class="muted">{{ p.storeName || stores.find((s) => s.id === p.storeId)?.name }}</view
          ><view class="muted">{{ p.description || '现做校园餐' }} · 剩余{{ p.remaining }}份</view
          ><view class="row" style="margin-top: 16rpx"
            ><text class="price">¥{{ p.promotion ? cents(p.promotion.priceCents) : p.price }}</text
            ><text v-if="p.promotion" class="muted" style="text-decoration: line-through"
              >¥{{ p.price }}</text
            ><button class="primary small-button" :disabled="!p.remaining" @click="searchPick(p)">
              选餐
            </button></view
          ></view
        ></view
      ><text
        class="tap"
        style="display: block; margin-top: 16rpx"
        @click="favorite(p.itemType, p.id, p.storeId)"
        >收藏餐品</text
      ></view
    ><view v-if="!products.length" class="empty">暂无餐品，请切换店铺或搜索条件。</view
    ><view class="modal" v-if="chosen" @click="chosen = undefined"
      ><view class="card" @click.stop
        ><view class="row"
          ><view class="section-title">{{ chosen.name }}</view
          ><text class="tap" @click="chosen = undefined">关闭</text></view
        ><view v-for="f in chosen.flavors || []" :key="f.name"
          ><view class="field-label">{{ f.name }}</view
          ><view class="options"
            ><text
              v-for="v in f.values"
              :key="v"
              class="option"
              :class="{ selected: flavors[f.name] === v }"
              @click="flavors[f.name] = v"
              >{{ v }}</text
            ></view
          ></view
        ><button class="primary" :loading="busy" @click="add">加入本店购物车</button></view
      ></view
    ><view class="modal" v-if="showReviews" @click="showReviews = false"
      ><view class="card" @click.stop
        ><view class="row"
          ><text>店铺评价</text><text class="tap" @click="showReviews = false">关闭</text></view
        ><scroll-view scroll-y style="max-height: 60vh"
          ><view class="card" v-for="(r, i) in reviews" :key="i"
            ><text>{{ r.rating }} 星</text><view>{{ r.content }}</view
            ><view class="muted" v-if="r.reply">商家回复：{{ r.reply }}</view></view
          ><view v-if="!reviews.length" class="empty">暂无评价</view></scroll-view
        ></view
      ></view
    ></view
  >
</template>

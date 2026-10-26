<script setup lang="ts">
import { ref, watch } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { api, toast, loggedIn, image, ApiError } from '../../api';
import { useSession } from '../../stores/session';
import {
  cents,
  type Campus,
  type Store,
  type Product,
  type Cart,
  type CursorPage,
  mergeRecords,
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
  reviews = ref<{ id: string; rating: number; content: string; reply?: string }[]>([]),
  reviewsMore = ref(false), reviewsLoading = ref(false),
  showReviews = ref(false),
  storesMore = ref(false), storesLoading = ref(false),
  searchMore = ref(false), matchedMore = ref(false), searchLoading = ref(false), matchedLoading = ref(false);
let menuSequence = 0, storeSequence = 0, searchSequence = 0, loadSequence = 0, active = true;
let storeCursor: string | null = null, searchCursor: string | null = null, matchedCursor: string | null = null;
let loadedCampus = '', appliedSearch = '';
let reviewSequence = 0;
let loadingCampuses = false;
const currentSession = () => session.session?.token || '';
function closeReviews() {
  reviewSequence++; showReviews.value = false; reviewsLoading.value = false;
}
watch(() => store.value?.id, () => { closeReviews(); reviews.value = []; reviewsMore.value = false; });
function invalidate() {
  menuSequence++; storeSequence++; searchSequence++; loadSequence++;
  closeReviews();
  storesLoading.value = false; searchLoading.value = false; matchedLoading.value = false;
}
watch(currentSession, token => {
  if (!token && loadingCampuses) {
    menuSequence++; storeSequence++; searchSequence++;
    storesLoading.value = false; searchLoading.value = matchedLoading.value = false;
  } else invalidate();
  stores.value = []; matchedStores.value = []; products.value = []; cart.value = undefined;
  store.value = undefined; chosen.value = undefined;
  storeCursor = searchCursor = matchedCursor = null;
  storesMore.value = searchMore.value = matchedMore.value = false;
  if (active && (token || !loadingCampuses)) void load();
}, { flush: 'sync' });
async function load() {
  const sequence = ++loadSequence, token = currentSession();
  loadingCampuses = true;
  try {
    const rows = await api<Campus[]>('/campuses');
    if (!active || sequence !== loadSequence || (token !== currentSession() && currentSession())) return;
    loadingCampuses = false;
    campuses.value = rows;
    campus.value = String(uni.getStorageSync('campus-choice') || campus.value || campuses.value[0]?.id || '');
    if (!campuses.value.some((c) => c.id === campus.value))
      campus.value = campuses.value[0]?.id || '';
    await loadStores();
  } catch (e) {
    if (active && sequence === loadSequence && token === currentSession()) toast(e);
  } finally {
    if (sequence === loadSequence) loadingCampuses = false;
  }
}
async function loadStores(reset = true) {
  if (!reset && (storesLoading.value || !storesMore.value)) return;
  const sequence = ++storeSequence, token = currentSession(), targetCampus = campus.value;
  storesLoading.value = true;
  if (loadedCampus !== targetCampus) {
    stores.value = []; store.value = undefined; storeCursor = null; storesMore.value = false;
    loadedCampus = targetCampus;
  }
  try {
    uni.setStorageSync('campus-choice', campus.value);
    const params = 'campusId=' + encodeURIComponent(targetCampus) + '&limit=50'
      + (!reset && storeCursor ? '&cursor=' + encodeURIComponent(storeCursor) : '');
    const result = await api<CursorPage<Store>>('/stores/page?' + params);
    if (!active || sequence !== storeSequence || campus.value !== targetCampus || token !== currentSession()) return;
    let rows = mergeRecords(reset ? [] : stores.value, result.records, row => row.id);
    if (reset) {
      const previous = String(uni.getStorageSync('store-choice') || store.value?.id || '');
      let selected = rows.find(s => s.id === previous);
      if (previous && !selected) {
        try {
          const detail = await api<Store>('/stores/' + previous);
          if (detail.campusId === targetCampus) { selected = detail; rows = mergeRecords(rows, [detail], row => row.id); }
        } catch (e) { if (!(e instanceof ApiError && e.status === 404)) throw e; }
      }
      if (!active || sequence !== storeSequence || token !== currentSession() || campus.value !== targetCampus) return;
      store.value = selected || rows[0];
    }
    stores.value = rows; storeCursor = result.nextCursor; storesMore.value = result.hasMore;
    if (reset) await menu();
  } catch (e) {
    if (active && sequence === storeSequence && token === currentSession()) toast(e);
  } finally {
    if (active && sequence === storeSequence && token === currentSession()) storesLoading.value = false;
  }
}
async function menu() {
  searchSequence++;
  searchLoading.value = false; matchedLoading.value = false;
  if (campusSearch.value) { products.value = []; cart.value = undefined; }
  campusSearch.value = false;
  matchedStores.value = [];
  const target = store.value;
  if (!target) {
    products.value = [];
    cart.value = undefined;
    return;
  }
  const sequence = ++menuSequence, token = currentSession();
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
    if (!active || sequence !== menuSequence || store.value?.id !== target.id || token !== currentSession()) return;
    products.value = items;
    cart.value = currentCart;
    categories.value = groups;
  } catch (e) {
    if (active && sequence === menuSequence && token === currentSession()) toast(e);
  }
}

function campusChanged(e: { detail: { value: unknown } }) {
  invalidate();
  campus.value = campuses.value[Number(e.detail.value)]!.id;
  store.value = undefined;
  query.value = '';
  category.value = '';
  products.value = []; matchedStores.value = []; cart.value = undefined;
  loadStores();
}
function storeChanged(e: { detail: { value: unknown } }) {
  store.value = stores.value[Number(e.detail.value)];
  query.value = '';
  category.value = '';
  products.value = []; cart.value = undefined;
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
function searchParams() {
  return 'campusId=' + encodeURIComponent(campus.value) + '&query=' + encodeURIComponent(query.value.trim()) + '&limit=50'
    + (openFilter.value ? '&open=' + String(openFilter.value === 1) : '');
}
async function searchCampus() {
  menuSequence++;
  const sequence = ++searchSequence, token = currentSession(), scope = searchParams();
  if (scope !== appliedSearch) {
    products.value = []; matchedStores.value = [];
    searchCursor = matchedCursor = null; searchMore.value = matchedMore.value = false;
  }
  appliedSearch = scope;
  campusSearch.value = true;
  chosen.value = undefined;
  searchLoading.value = matchedLoading.value = true;
  try {
    const [results, shops] = await Promise.all([
      api<CursorPage<Product & { storeName?: string }>>('/search/page?' + scope),
      api<CursorPage<Store>>('/stores/page?' + scope),
    ]);
    if (active && sequence === searchSequence && token === currentSession() && scope === searchParams()) {
      products.value = mergeRecords([], results.records, row => row.storeId + ':' + row.itemType + ':' + row.id);
      matchedStores.value = mergeRecords([], shops.records, row => row.id);
      searchCursor = results.nextCursor; searchMore.value = results.hasMore;
      matchedCursor = shops.nextCursor; matchedMore.value = shops.hasMore;
    }
  } catch (e) {
    if (active && sequence === searchSequence && token === currentSession()) toast(e);
  } finally {
    if (active && sequence === searchSequence && token === currentSession()) searchLoading.value = matchedLoading.value = false;
  }
}
async function moreSearch(shops = false) {
  if (!campusSearch.value) return;
  if (searchParams() !== appliedSearch) { await searchCampus(); return; }
  const loading = shops ? matchedLoading : searchLoading, more = shops ? matchedMore : searchMore;
  const cursor = shops ? matchedCursor : searchCursor;
  if (loading.value || !more.value || !cursor) return;
  const sequence = searchSequence, token = currentSession(), scope = appliedSearch;
  loading.value = true;
  try {
    const path = (shops ? '/stores/page?' : '/search/page?') + scope + '&cursor=' + encodeURIComponent(cursor);
    if (shops) {
      const page = await api<CursorPage<Store>>(path);
      if (!active || sequence !== searchSequence || token !== currentSession() || scope !== searchParams()) return;
      matchedStores.value = mergeRecords(matchedStores.value, page.records, row => row.id);
      matchedCursor = page.nextCursor; matchedMore.value = page.hasMore;
    } else {
      const page = await api<CursorPage<Product & { storeName?: string }>>(path);
      if (!active || sequence !== searchSequence || token !== currentSession() || scope !== searchParams()) return;
      products.value = mergeRecords(products.value, page.records, row => row.storeId + ':' + row.itemType + ':' + row.id);
      searchCursor = page.nextCursor; searchMore.value = page.hasMore;
    }
  } catch (e) {
    if (active && sequence === searchSequence && token === currentSession()) toast(e);
  } finally {
    if (active && sequence === searchSequence && token === currentSession()) loading.value = false;
  }
}
async function openReviews(more = false) {
  if (!store.value || more && (reviewsLoading.value || !reviewsMore.value)) return;
  const targetStore = store.value.id, token = currentSession(), sequence = ++reviewSequence;
  reviewsLoading.value = true;
  if (!more) { reviews.value = []; reviewsMore.value = false; }
  try {
    const before = more ? reviews.value.at(-1)?.id : undefined;
    const result = await api<{ records: typeof reviews.value }>('/stores/' + targetStore
      + '/reviews?limit=100' + (before ? '&beforeId=' + encodeURIComponent(before) : ''));
    if (!active || sequence !== reviewSequence || store.value?.id !== targetStore || token !== currentSession()) return;
    reviews.value = more ? mergeRecords(reviews.value, result.records, row => row.id) : result.records;
    reviewsMore.value = result.records.length === 100;
    showReviews.value = true;
  } catch (e) {
    if (active && sequence === reviewSequence && store.value?.id === targetStore && token === currentSession()) toast(e);
  } finally {
    if (sequence === reviewSequence) reviewsLoading.value = false;
  }
}
async function searchPick(p: Product) {
  if (p.storeId !== store.value?.id) {
    const sequence = searchSequence, token = currentSession(), targetCampus = campus.value;
    try {
      const selected = stores.value.find(s => s.id === p.storeId) || await api<Store>('/stores/' + p.storeId);
      if (!active || sequence !== searchSequence || token !== currentSession() || selected.campusId !== targetCampus) return;
      stores.value = mergeRecords(stores.value, [selected], row => row.id);
      store.value = selected; category.value = ''; query.value = '';
      await menu();
      if (store.value?.id === selected.id && token === currentSession()) pick(p);
    } catch (e) { if (active && sequence === searchSequence && token === currentSession()) toast(e); }
  } else pick(p);
}
function visitStore(s: Store) {
  stores.value = mergeRecords(stores.value, [s], row => row.id);
  store.value = s;
  category.value = '';
  query.value = '';
  menu();
}
const nav = (url: string) => uni.navigateTo({ url });
onShow(() => { active = true; void load(); });
onHide(() => { active = false; invalidate(); });
</script>
<template>
  <view class="screen menu-screen">
    <view class="row menu-nav">
      <view class="menu-brand">CampusEats</view>
      <!-- #ifdef H5 -->
      <a href="/admin/" class="tap" style="text-decoration: none">商家/配送工作台</a>
      <!-- #endif -->
      <!-- #ifdef H5 -->
      <a class="menu-login" :href="session.session ? '/app/pages/account/index' : '/app/pages/login/index'">{{ session.session?.name || '登录点餐' }}</a>
      <!-- #endif -->
      <!-- #ifndef H5 -->
      <button class="menu-login" @click="nav(session.session ? '/pages/account/index' : '/pages/login/index')">{{ session.session?.name || '登录点餐' }}</button>
      <!-- #endif -->
    </view>
    <view class="hero"
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
      ><button v-if="storesMore" class="secondary small-button" :loading="storesLoading" :disabled="storesLoading" @click="loadStores(false)">加载更多店铺</button></view
    ><view class="card"
      ><input v-model="query" aria-label="搜索餐品或店铺名称" placeholder="搜索餐品或店铺名称" @confirm="menu" />
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
      ><text class="tap" @click="openReviews()">店铺评价</text></view
    ><template v-if="campusSearch"><view class="section-title">店铺搜索结果</view><view class="card row" v-for="s in matchedStores" :key="s.id"><view>{{ s.name }}<view class="muted">{{ s.accepting ? '营业中' : '休息中' }} · {{ s.hours.join(' / ') }}</view></view><text class="tap" @click="visitStore(s)">进入店铺</text></view><view v-if="!matchedStores.length && !matchedLoading" class="muted">没有匹配的店铺。</view><button v-if="matchedMore" class="secondary small-button" :loading="matchedLoading" :disabled="matchedLoading" @click="moreSearch(true)">加载更多匹配店铺</button><view class="section-title">餐品搜索结果</view></template
    ><view class="card menu-product" v-for="p in products" :key="p.storeId + p.itemType + p.id"
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
    ><button v-if="campusSearch && searchMore" class="secondary small-button" :loading="searchLoading" :disabled="searchLoading" @click="moreSearch()">加载更多餐品</button><view v-if="!products.length && !searchLoading" class="empty">暂无餐品，请切换店铺或搜索条件。</view
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
    ><view class="modal" v-if="showReviews" @click="closeReviews"
      ><view class="card" @click.stop
        ><view class="row"
          ><text>店铺评价</text><text class="tap" @click="closeReviews">关闭</text></view
        ><scroll-view scroll-y style="max-height: 60vh"
          ><view class="card" v-for="r in reviews" :key="r.id"
            ><text>{{ r.rating }} 星</text><view>{{ r.content }}</view
            ><view class="muted" v-if="r.reply">商家回复：{{ r.reply }}</view></view
          ><view v-if="!reviews.length" class="empty">暂无评价</view>
          <button v-if="reviewsMore" class="secondary" :loading="reviewsLoading" @click="openReviews(true)">加载更早评价</button></scroll-view
        ></view
      ></view
    ></view
  >
</template>

<style scoped>
.menu-nav { flex-wrap: wrap; margin-bottom: 24rpx; gap: 16rpx; }
.menu-brand { font-size: 36rpx; color: #173f35; font-weight: 750; letter-spacing: -1rpx; margin-right: auto; }
.menu-nav a { font-size: 24rpx; padding: 16rpx 0; }
.menu-login { text-decoration: none; margin: 0; padding: 8rpx 18rpx; font-size: 24rpx; line-height: 48rpx; color: #173f35; background: #e3ebe3; border-radius: 6rpx; }
.menu-login::after { border: 0; }
.menu-product .card-name { margin-bottom: 8rpx; }
.menu-product > .row > view { min-width: 0; }
.menu-product .row .row { flex-wrap: wrap; }
.menu-screen .hero { margin-bottom: 24rpx; }
/* #ifdef H5 */
.menu-screen { padding-bottom: 80px; }
.menu-nav a { min-height: 44px; display: inline-flex; align-items: center; font-size: 13px; }
.menu-login { min-height: 44px; font-size: 14px; line-height: 28px; }
.menu-screen .title { font-size: 30px; }
.menu-screen .muted, .menu-screen .subtitle { font-size: 14px; }
.menu-screen .tap { font-size: 14px; }
.menu-screen .small-button { min-height: 44px; font-size: 14px; line-height: 44px; }
.menu-screen .card-name { font-size: 18px; }
@media (min-width: 600px) {
  .menu-screen { max-width: 760px; padding: 24px 24px 88px; }
  .menu-screen .hero { padding: 28px; }
  .menu-screen .card { padding: 24px; margin-bottom: 18px; }
  .menu-screen .title { font-size: 36px; }
}
/* #endif */
</style>

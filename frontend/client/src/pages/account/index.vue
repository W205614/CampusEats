<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { api, toast, loggedIn, ApiError } from '../../api';
import { useSession } from '../../stores/session';
import { usePagedList } from '../../paged-list';
import { cents, states, mergeRecords, type CursorPage, type Store, type Coupon } from '../../../../contracts/platform';
const session = useSession();
const stores = ref<Store[]>([]),
  selected = ref(''),
  walletState = ref(''), storesMore = ref(false), storesLoading = ref(false);
let cursor: string | null = null, sequence = 0, storeSequence = 0, couponSequence = 0, active = true;
type Favorite = { targetType: string; targetId: string; storeId: string; storeName: string; name: string };
const walletPage = usePagedList<Coupon>(() => '/wallet/page' + (walletState.value ? '?state=' + walletState.value : ''), row => row.id, () => active);
const favoritePage = usePagedList<Favorite>(() => '/favorites/page', row => row.storeId + ':' + row.targetType + ':' + row.targetId, () => active);
const platformCouponPage = usePagedList<Coupon>(() => '/coupons/page', row => row.id, () => active);
const storeCouponPage = usePagedList<Coupon>(() => '/stores/' + selected.value + '/coupons/page', row => row.id, () => active);
const wallet = walletPage.records, favorites = favoritePage.records;
const walletMore = walletPage.hasMore, walletLoading = walletPage.loading, favoriteMore = favoritePage.hasMore, favoriteLoading = favoritePage.loading;
const platformCouponMore = platformCouponPage.hasMore, platformCouponLoading = platformCouponPage.loading;
const storeCouponMore = storeCouponPage.hasMore, storeCouponLoading = storeCouponPage.loading;
const coupons = computed(() => mergeRecords(platformCouponPage.records.value, storeCouponPage.records.value, row => row.id));
const accountLoaded = computed(() => walletPage.loaded.value && favoritePage.loaded.value);
const couponsLoaded = computed(() => platformCouponPage.loaded.value && (!selected.value || storeCouponPage.loaded.value));
const currentSession = () => session.session?.token || '';
watch(currentSession, () => {
  sequence++; storeSequence++; couponSequence++;
  stores.value = []; selected.value = ''; walletState.value = '';
  walletPage.clear(); favoritePage.clear(); platformCouponPage.clear(); storeCouponPage.clear();
  cursor = null; storesMore.value = false; storesLoading.value = false;
  if (active && session.session) void load();
}, { flush: 'sync' });
async function loadStores(reset = false) {
  if (!reset && (storesLoading.value || !storesMore.value)) return;
  const current = ++storeSequence, token = currentSession();
  storesLoading.value = true;
  try {
    const params = 'limit=50' + (!reset && cursor ? '&cursor=' + encodeURIComponent(cursor) : '');
    const page = await api<CursorPage<Store>>('/stores/page?' + params);
    if (!active || current !== storeSequence || token !== currentSession()) return;
    let rows = mergeRecords(reset ? [] : stores.value, page.records, row => row.id);
    if (reset) {
      const previous = selected.value || String(uni.getStorageSync('store-choice') || '');
      if (previous && !rows.some(s => s.id === previous)) {
        try { rows = mergeRecords(rows, [await api<Store>('/stores/' + previous)], row => row.id); }
        catch (e) { if (!(e instanceof ApiError && e.status === 404)) throw e; }
      }
      if (!active || current !== storeSequence || token !== currentSession()) return;
      if (!rows.some(s => s.id === selected.value)) selected.value = rows.find(s => s.id === previous)?.id || rows[0]?.id || '';
    }
    stores.value = rows; cursor = page.nextCursor; storesMore.value = page.hasMore;
  } catch (e) { if (active && current === storeSequence && token === currentSession()) toast(e); }
  finally { if (active && current === storeSequence && token === currentSession()) storesLoading.value = false; }
}
async function load(refreshStores = true) {
  if (!loggedIn()) return;
  const current = ++sequence, token = currentSession();
  try {
    if (refreshStores || !stores.value.length) await loadStores(true);
    if (!active || current !== sequence || token !== currentSession()) return;
    await Promise.all([walletPage.load(), favoritePage.load()]);
    if (!active || current !== sequence || token !== currentSession()) return;
    await available();
  } catch (e) {
    if (active && current === sequence && token === currentSession()) toast(e);
  }
}
async function available() {
  const current = ++couponSequence, token = currentSession(), target = selected.value;
  try {
    await Promise.all([platformCouponPage.load(), target ? storeCouponPage.load() : Promise.resolve(storeCouponPage.clear())]);
    if (!active || current !== couponSequence || token !== currentSession() || target !== selected.value) return;
  } catch (e) {
    if (active && current === couponSequence && token === currentSession()) toast(e);
  }
}
async function claim(c: Coupon) {
  try {
    await api('/coupons/' + c.id + '/claim', 'POST');
    uni.showToast({ title: '领取成功', icon: 'success' });
    await load(false);
  } catch (e) {
    toast(e);
  }
}
async function remove(f: (typeof favorites.value)[number]) {
  try {
    await api('/stores/' + f.storeId + '/favorites/' + f.targetType + '/' + f.targetId, 'DELETE');
    await load(false);
  } catch (e) {
    toast(e);
  }
}
async function visit(f: (typeof favorites.value)[number]) {
  const current = sequence, token = currentSession();
  try {
    const s = stores.value.find(s => s.id === f.storeId) || await api<Store>('/stores/' + f.storeId);
    if (!active || current !== sequence || token !== currentSession()) return;
    uni.setStorageSync('campus-choice', s.campusId);
    uni.setStorageSync('store-choice', s.id);
    uni.switchTab({ url: '/pages/menu/index' });
  } catch (e) { if (active && current === sequence && token === currentSession()) toast(e); }
}
const navTo = (url: string) => uni.navigateTo({ url });
onShow(() => { active = true; void load(); });
onHide(() => { active = false; sequence++; storeSequence++; couponSequence++; storesLoading.value = false;
  walletPage.cancel(); favoritePage.cancel(); platformCouponPage.cancel(); storeCouponPage.cancel(); });
</script>
<template>
  <view class="screen"
    ><view class="title">我的校园生活</view
    ><view class="row"
      ><text class="tap" @click="navTo('/pages/addresses/index')">收货地址</text
      ><text class="tap" @click="navTo('/pages/login/index')">账户与退出</text>
      <text class="tap" @click="navTo('/pages/messages/index')">站内消息</text></view
    ><view class="section-title">领券中心</view
    ><picker
      :range="stores.map((s) => s.campusName + ' / ' + s.name)"
      :value="stores.findIndex((s) => s.id === selected)"
      @change="
        selected = stores[Number($event.detail.value)]!.id;
        available();
      "
      ><view class="card tap"
        >{{ stores.find((s) => s.id === selected)?.name || '选择店铺' }} ▾</view
      ></picker><button v-if="storesMore" class="secondary small-button" :loading="storesLoading" :disabled="storesLoading" @click="loadStores()">加载更多领券店铺</button
    ><view class="card row" v-for="c in coupons" :key="c.id"
      ><view
        ><view>{{ c.name }} · {{ c.storeId ? '店铺券' : '平台券' }}</view
        ><view class="muted"
          >满¥{{ cents(c.thresholdCents) }}，{{
            c.kind === 'REDUCTION' ? '减¥' + cents(c.value) : Number(c.value) / 1000 + '折'
          }}</view
        ></view
      ><button class="primary small-button" @click="claim(c)">领取</button></view
    ><button v-if="platformCouponMore" class="secondary small-button" :loading="platformCouponLoading" :disabled="platformCouponLoading" @click="platformCouponPage.load(false)">加载更多平台券</button
    ><button v-if="storeCouponMore" class="secondary small-button" :loading="storeCouponLoading" :disabled="storeCouponLoading" @click="storeCouponPage.load(false)">加载更多店铺券</button
    ><view v-if="couponsLoaded && !coupons.length" class="empty">当前暂无可领取的优惠券，可以切换店铺看看。</view
    ><view class="section-title">我的优惠券</view
    ><picker :range="['全部状态','可用','已锁定','已使用','已过期']" :value="['','AVAILABLE','LOCKED','USED','EXPIRED'].indexOf(walletState)" @change="walletState = ['','AVAILABLE','LOCKED','USED','EXPIRED'][Number($event.detail.value)] || ''; walletPage.load()"><view class="card tap">筛选优惠券：{{ states[walletState] || '全部状态' }} ▾</view></picker
    ><view class="card" v-for="c in wallet" :key="c.id"
      ><view class="row"
        ><text>{{ c.name }}</text
        ><text>{{ states[c.state || ''] || c.state }}</text></view
      ><view class="muted"
        >{{ c.storeId ? '店铺专用' : '平台通用' }} · 到期 {{ c.endsAt.slice(0, 10) }}</view
      ></view
    ><button v-if="walletMore" class="secondary small-button" :loading="walletLoading" :disabled="walletLoading" @click="walletPage.load(false)">加载更多优惠券</button
    ><view v-if="accountLoaded && !wallet.length" class="empty">还没有优惠券，领取后会保存在这里。</view
    ><view class="section-title">我的收藏</view
    ><view class="card" v-for="f in favorites" :key="f.storeId + f.targetType + f.targetId"
      ><view>{{ f.name || '已停用商品' }} · {{ f.storeName }}</view
      ><view class="row"
        ><text class="tap" @click="visit(f)">进入店铺</text
        ><text class="tap" @click="remove(f)">取消收藏</text></view
      ></view
    ><button v-if="favoriteMore" class="secondary small-button" :loading="favoriteLoading" :disabled="favoriteLoading" @click="favoritePage.load(false)">加载更多收藏</button
    ><view v-if="accountLoaded && !favorites.length" class="empty">还没有收藏，去点餐页收藏喜欢的店铺和餐品吧。</view
    ></view
  >
</template>

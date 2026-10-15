<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { api, toast, loggedIn } from '../../api';
import { cents, states, type Store, type Coupon } from '../../../../contracts/platform';
const stores = ref<Store[]>([]),
  selected = ref(''),
  accountLoaded = ref(false),
  couponsLoaded = ref(false),
  coupons = ref<Coupon[]>([]),
  wallet = ref<Coupon[]>([]),
  favorites = ref<
    { targetType: string; targetId: string; storeId: string; storeName: string; name: string }[]
  >([]);
async function load() {
  if (!loggedIn()) return;
  accountLoaded.value = false;
  try {
    stores.value = await api<Store[]>('/stores');
    if (!stores.value.some((s) => s.id === selected.value)) {
      const previous = String(uni.getStorageSync('store-choice') || '');
      selected.value = stores.value.find((s) => s.id === previous)?.id || stores.value[0]?.id || '';
    }
    [wallet.value, favorites.value] = await Promise.all([
      api<Coupon[]>('/wallet'),
      api<typeof favorites.value>('/favorites'),
    ]);
    accountLoaded.value = true;
    await available();
  } catch (e) {
    toast(e);
  }
}
async function available() {
  couponsLoaded.value = false;
  try {
    const platform = await api<Coupon[]>('/coupons');
    const local = selected.value
      ? await api<Coupon[]>('/stores/' + selected.value + '/coupons')
      : [];
    coupons.value = [...platform, ...local];
    couponsLoaded.value = true;
  } catch (e) {
    toast(e);
  }
}
async function claim(c: Coupon) {
  try {
    await api('/coupons/' + c.id + '/claim', 'POST');
    uni.showToast({ title: '领取成功', icon: 'success' });
    await load();
  } catch (e) {
    toast(e);
  }
}
async function remove(f: (typeof favorites.value)[number]) {
  try {
    await api('/stores/' + f.storeId + '/favorites/' + f.targetType + '/' + f.targetId, 'DELETE');
    await load();
  } catch (e) {
    toast(e);
  }
}
function visit(f: (typeof favorites.value)[number]) {
  const s = stores.value.find((s) => s.id === f.storeId);
  if (!s) {
    toast(new Error('店铺当前不可访问'));
    return;
  }
  uni.setStorageSync('campus-choice', s.campusId);
  uni.setStorageSync('store-choice', s.id);
  uni.switchTab({ url: '/pages/menu/index' });
}
const navTo = (url: string) => uni.navigateTo({ url });
onShow(load);
</script>
<template>
  <view class="screen"
    ><view class="title">我的校园生活</view
    ><view class="row"
      ><text class="tap" @click="navTo('/pages/addresses/index')">收货地址</text
      ><text class="tap" @click="navTo('/pages/login/index')">账户与退出</text></view
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
      ></picker
    ><view class="card row" v-for="c in coupons" :key="c.id"
      ><view
        ><view>{{ c.name }} · {{ c.storeId ? '店铺券' : '平台券' }}</view
        ><view class="muted"
          >满¥{{ cents(c.thresholdCents) }}，{{
            c.kind === 'REDUCTION' ? '减¥' + cents(c.value) : Number(c.value) / 1000 + '折'
          }}</view
        ></view
      ><button class="primary small-button" @click="claim(c)">领取</button></view
    ><view v-if="couponsLoaded && !coupons.length" class="empty">当前暂无可领取的优惠券，可以切换店铺看看。</view
    ><view class="section-title">我的优惠券</view
    ><view class="card" v-for="c in wallet" :key="c.id"
      ><view class="row"
        ><text>{{ c.name }}</text
        ><text>{{ states[c.state || ''] || c.state }}</text></view
      ><view class="muted"
        >{{ c.storeId ? '店铺专用' : '平台通用' }} · 到期 {{ c.endsAt.slice(0, 10) }}</view
      ></view
    ><view v-if="accountLoaded && !wallet.length" class="empty">还没有优惠券，领取后会保存在这里。</view
    ><view class="section-title">我的收藏</view
    ><view class="card" v-for="f in favorites" :key="f.storeId + f.targetType + f.targetId"
      ><view>{{ f.name || '已停用商品' }} · {{ f.storeName }}</view
      ><view class="row"
        ><text class="tap" @click="visit(f)">进入店铺</text
        ><text class="tap" @click="remove(f)">取消收藏</text></view
      ></view
    ><view v-if="accountLoaded && !favorites.length" class="empty">还没有收藏，去点餐页收藏喜欢的店铺和餐品吧。</view
    ></view
  >
</template>

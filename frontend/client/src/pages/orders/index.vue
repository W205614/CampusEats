<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { api, toast, loggedIn, confirm, base } from '../../api';
import { useSession } from '../../stores/session';
import { readPages } from '../../../../contracts/paging';
import {
  cents,
  states,
  orderStatus,
  type Order,
  type Checkout,
  type Aftersale,
} from '../../../../contracts/platform';
const session = useSession(),
  orders = ref<Order[]>([]),
  checkouts = ref<Checkout[]>([]),
  requests = ref<Aftersale[]>([]),
  chosen = ref<Order>(),
  mode = ref(''),
  reason = ref(''),
  rating = ref(5),
  content = ref(''),
  quantities = ref<Record<string, number>>({}),
  images = ref<string[]>([]),
  busy = ref(false),
  loading = ref(false),
  hasMore = ref(false);
let loadedPages = 1;
let timer: ReturnType<typeof setInterval> | undefined;
async function load(pages = loadedPages) {
  if (!loggedIn() || loading.value) return;
  loading.value = true;
  try {
    const [orderPages, checkoutPages, aftersales] = await Promise.all([
      readPages((before) => api<Order[]>('/orders' + (before ? '?beforeId=' + before : '')), pages),
      readPages((before) => api<Checkout[]>('/checkouts' + (before ? '?beforeId=' + before : '')), pages),
      api<Aftersale[]>('/aftersales'),
    ]);
    orders.value = orderPages.items;
    checkouts.value = checkoutPages.items;
    requests.value = aftersales;
    hasMore.value = orderPages.hasMore || checkoutPages.hasMore;
    loadedPages = pages;
    if (chosen.value) chosen.value = orders.value.find((o) => o.id === chosen.value?.id);
  } catch (e) {
    toast(e);
  } finally {
    loading.value = false;
  }
}
const loadMore = () => load(loadedPages + 1);
const refresh = () => load(1);
async function pay(c: Checkout) {
  if (busy.value) return;
  busy.value = true;
  try {
    const result = await api<Checkout>('/checkouts/' + c.id + '/pay', 'POST', {
      version: c.version,
    });
    if (result.state !== 'PAID') throw new Error('结算单已过期或取消');
    uni.showToast({ title: '模拟支付成功', icon: 'success' });
    await load();
  } catch (e) {
    toast(e);
    await load();
  } finally {
    busy.value = false;
  }
}
async function cancelCheckout(c: Checkout) {
  if (!(await confirm('取消该结算单的全部未支付订单？'))) return;
  try {
    await api('/checkouts/' + c.id + '/cancel', 'POST', { version: c.version });
    await load();
  } catch (e) {
    toast(e);
  }
}
function show(o: Order, m: string) {
  chosen.value = o;
  mode.value = m;
  reason.value = '';
  content.value = '';
  quantities.value = {};
  images.value = [];
}
async function submit() {
  if (!chosen.value || busy.value) return;
  busy.value = true;
  try {
    const o = chosen.value;
    if (mode.value === 'REVIEW')
      await api('/orders/' + o.id + '/review', 'POST', {
        rating: rating.value,
        content: content.value,
      });
    else if (mode.value === 'DIRECT')
      await api('/orders/' + o.id + '/actions/cancel', 'POST', {
        version: o.version,
        reason: reason.value,
      });
    else
      await api('/orders/' + o.id + '/aftersales', 'POST', {
        kind: mode.value,
        reason: reason.value,
        items:
          mode.value === 'PARTIAL'
            ? Object.entries(quantities.value)
                .filter(([, n]) => n > 0)
                .map(([detailId, quantity]) => ({ detailId, quantity: Number(quantity) }))
            : null,
        evidenceIds: images.value,
      });
    chosen.value = undefined;
    await load();
    uni.showToast({ title: '已提交', icon: 'success' });
  } catch (e) {
    toast(e);
  } finally {
    busy.value = false;
  }
}
async function upload() {
  if (!chosen.value || images.value.length >= 3) return;
  try {
    const result = await new Promise<UniApp.ChooseImageSuccessCallbackResult>((resolve, reject) =>
      uni.chooseImage({ count: 3 - images.value.length, success: resolve, fail: reject }),
    );
    for (const filePath of result.tempFilePaths) {
      const data = await new Promise<{ id: string }>((resolve, reject) =>
        uni.uploadFile({
          url: base() + '/api/v1/user/orders/' + chosen.value!.id + '/evidence',
          filePath,
          name: 'file',
          header: { Authorization: 'Bearer ' + session.session?.token },
          success: (r) => {
            try {
              const value = JSON.parse(r.data);
              if (r.statusCode >= 300) reject(new Error(value.message));
              else resolve(value.data);
            } catch (e) {
              reject(e);
            }
          },
          fail: reject,
        }),
      );
      images.value.push(data.id);
    }
  } catch (e) {
    toast(e);
  }
}
async function appeal(a: Aftersale) {
  try {
    await api('/aftersales/' + a.id + '/appeal', 'POST', { version: a.version });
    await load();
  } catch (e) {
    toast(e);
  }
}
async function withdraw(a: Aftersale) {
  try {
    await api('/aftersales/' + a.id + '/withdraw', 'POST', { version: a.version });
    await load();
  } catch (e) {
    toast(e);
  }
}
function quantityChanged(id: string, event: { detail: unknown }) {
  const d = event.detail;
  quantities.value[id] = Number(d && typeof d === 'object' && 'value' in d ? d.value : d) || 0;
}
async function reorder(o: Order) {
  try {
    const cart = await api<{ cartVersion: string }>('/stores/' + o.storeId + '/cart');
    const result = await api<{ skipped: string[] }>('/orders/' + o.id + '/reorder', 'POST', {
      version: cart.cartVersion,
    });
    if (result.skipped.length)
      uni.showModal({
        title: '部分餐品需重新选择',
        content: result.skipped.join('；'),
        showCancel: false,
      });
    else uni.showToast({ title: '已加入原店购物车', icon: 'none' });
  } catch (e) {
    toast(e);
  }
}
async function remind(o: Order) {
  try {
    await api('/orders/' + o.id + '/reminder', 'POST');
    uni.showToast({ title: '已通知店铺', icon: 'success' });
  } catch (e) {
    toast(e);
  }
}
function stop() {
  if (timer) clearInterval(timer);
  timer = undefined;
}
onShow(() => {
  load();
  stop();
  timer = setInterval(load, 15000);
});
onHide(stop);
onUnload(stop);
</script>
<template>
  <view class="screen"
    ><view class="row"><view class="title">我的订单</view><text class="tap" @click="refresh">重新加载</text></view
    ><view class="subtitle">合单支付，店铺分别履约。支付和退款均为模拟。</view
    ><view v-for="c in checkouts.filter((c) => c.state === 'PENDING')" :key="c.id" class="card"
      ><view class="row"
        ><text>待支付结算单 {{ c.id }}</text
        ><text class="price">¥{{ cents(c.totalCents) }}</text></view
      ><view class="row" style="margin-top: 20rpx"
        ><button class="secondary small-button" @click="cancelCheckout(c)">取消整批</button
        ><button class="primary small-button" :disabled="busy" @click="pay(c)">
          整批模拟支付
        </button></view
      ></view
    ><view class="card" v-for="o in orders" :key="o.id"
      ><view class="row"
        ><text>{{ o.campusName }} · {{ o.storeName }}</text
        ><text class="tag">{{ orderStatus[o.status] }}</text></view
      ><view class="muted">{{ o.number }} · {{ o.fulfillment === 'PICKUP' ? '自取' : '配送' }}</view
      ><view v-for="d in o.details" :key="d.id" style="margin-top: 14rpx"
        >{{ d.name }} × {{ d.number }}</view
      ><view class="row" style="margin-top: 20rpx"
        ><text>应付 ¥{{ o.amount }}</text
        ><text v-if="Number(o.refundedCents)">已退 ¥{{ cents(o.refundedCents) }}</text></view
      ><view class="notice" v-if="o.pickupCode && o.status === 3"
        >取餐码：<text style="font-size: 40rpx; font-weight: bold">{{ o.pickupCode }}</text
        ><view>到店后向员工出示，核销后完成订单。</view></view
      ><view class="row" style="margin-top: 24rpx; flex-wrap: wrap; gap: 12rpx"
        ><button v-if="[2, 3, 4].includes(o.status)" class="secondary small-button" @click="remind(o)">催单</button
        ><button v-if="[5, 6].includes(o.status)" class="secondary small-button" @click="reorder(o)">再来一单</button
        ><button v-if="o.status === 2" class="secondary small-button" @click="show(o, 'DIRECT')">
          取消订单</button
        ><button v-if="o.status === 3" class="secondary small-button" @click="show(o, 'CANCEL')">
          申请取消</button
        ><template v-if="o.status === 5"
          ><button class="secondary small-button" @click="show(o, 'PARTIAL')">部分退款</button
          ><button class="secondary small-button" @click="show(o, 'FULL')">整单退款</button
          ><button class="secondary small-button" @click="show(o, 'REVIEW')">
            评价订单
          </button></template
        ></view
      ><view class="muted" v-for="r in o.refunds" :key="r.id"
        >退款 ¥{{ cents(r.amountCents) }} · {{ states[r.state] || r.state }}</view
      ></view
    ><view v-if="!orders.length" class="empty">还没有订单，先去看看校园好店吧。</view
    ><button v-if="hasMore" class="secondary" :disabled="loading" @click="loadMore">加载更早订单与结算单</button
    ><view class="section-title" v-if="requests.length">售后进度</view
    ><view class="card" v-for="a in requests" :key="a.id"
      ><view class="row"
        ><text>申请 {{ a.id }}</text
        ><text>{{ states[a.state] || a.state }}</text></view
      ><view>{{ a.reason }} · ¥{{ cents(a.amountCents) }}</view
      ><view class="muted" v-if="a.decision">处理说明：{{ a.decision }}</view
      ><view class="row" style="margin-top: 20rpx"
        ><button v-if="a.state === 'PENDING'" class="secondary small-button" @click="withdraw(a)">
          撤销申请</button
        ><button
          v-if="!a.appealed && ['PENDING', 'REJECTED'].includes(a.state)"
          class="secondary small-button"
          @click="appeal(a)"
        >
          申请平台介入
        </button></view
      ></view
    ><view class="modal" v-if="chosen" @click="chosen = undefined"
      ><view class="card" @click.stop
        ><scroll-view scroll-y style="max-height: 75vh"
          ><view class="row"
            ><text class="section-title">{{
              mode === 'REVIEW' ? '评价订单' : mode === 'DIRECT' ? '取消订单' : '售后申请'
            }}</text
            ><text class="tap" @click="chosen = undefined">关闭</text></view
          ><template v-if="mode === 'REVIEW'"
            ><picker
              :range="[1, 2, 3, 4, 5]"
              :value="rating - 1"
              @change="rating = Number($event.detail.value) + 1"
              ><view class="field-label">评分：{{ rating }} 星 ▾</view></picker
            ><textarea
              v-model="content"
              placeholder="分享这次用餐感受"
              maxlength="1000"
            /></template
          ><template v-else
            ><view class="notice">完成后7天内可售后，按原实付退款；部分退款不退配送及包装费。</view
            ><view v-if="mode === 'PARTIAL'"
              ><view class="row" v-for="d in chosen.details" :key="d.id"
                ><view
                  >{{ d.name
                  }}<view class="muted"
                    >剩余可退 {{ d.number - d.refundedQuantity - d.reservedQuantity }} 份</view
                  ></view
                ><input
                  type="number"
                  :value="quantities[d.id] || 0"
                  @input="quantityChanged(d.id, $event)"
                  style="width: 100rpx; border: 1px solid #ddd; padding: 12rpx" /></view></view
            ><textarea v-model="reason" placeholder="请说明原因" maxlength="200" /><button
              v-if="mode !== 'DIRECT'"
              class="secondary"
              @click="upload"
            >
              添加举证图片（{{ images.length }} / 3）
            </button></template
          ><button
            class="primary"
            :disabled="busy || (mode === 'REVIEW' ? !content.trim() : !reason.trim())"
            :loading="busy"
            @click="submit"
          >
            确认提交
          </button></scroll-view
        ></view
      ></view
    ></view
  >
</template>

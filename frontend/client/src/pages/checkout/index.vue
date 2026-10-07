<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { api, toast, loggedIn, uuid, ApiError, confirm } from "../../api";
import { useSession } from "../../stores/session";
import type { Cart, Address, Quote, Order, SubmitRequest, PreviewRequest, Id } from "../../../../contracts/models";
const store = useSession(),
  addresses = ref<Address[]>([]),
  index = ref(0),
  quote = ref<Quote>(),
  remark = ref(""),
  busy = ref(false),
  cartVersion = ref("0"),
  pending = ref<{userId:Id|undefined;key:string;body:SubmitRequest}>();
function readPending() {
  try {
    const v = uni.getStorageSync("campus-pending-submit");
    pending.value = v?.userId === store.session?.id ? v : undefined;
  } catch {}
}
async function load() {
  if (!loggedIn()) return;
  readPending();
  try {
    const cart = await api<Cart>("/cart");
    cartVersion.value = cart.cartVersion;
    addresses.value = await api<Address[]>("/addresses");
    const d = addresses.value.findIndex((x) => x.isDefault);
    if (d >= 0) index.value = d;
    if (pending.value) return;
    if (addresses.value[index.value]) await preview();
  } catch (e) {
    toast(e);
  }
}
async function preview() {
  quote.value = undefined;
  const address = addresses.value[index.value];
  if (!address) return;
  try {
    quote.value = await api<Quote>("/checkout/preview", "POST", {
      addressId: address.id,
      cartVersion: cartVersion.value,
    } satisfies PreviewRequest);
  } catch (e) {
    toast(e);
  }
}
async function finish(order: Order) {
  uni.removeStorageSync("campus-pending-submit");
  pending.value = undefined;
  uni.showToast({ title: "订单已创建，待模拟支付", icon: "none" });
  uni.switchTab({ url: "/pages/orders/index" });
}
async function recover() {
  if (!pending.value || busy.value) return;
  busy.value = true;
  try {
    await finish(await api<Order>("/orders/by-request/" + pending.value.key));
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      try {
        await finish(
          await api<Order>("/orders", "POST", pending.value.body, {
            "Idempotency-Key": pending.value.key,
          }),
        );
      } catch (next) {
        await handleError(next);
      }
    } else toast(e);
  } finally {
    busy.value = false;
  }
}
async function handleError(e: unknown) {
  toast(e);
  if (
    e instanceof ApiError &&
    e.status >= 400 &&
    e.status < 500 &&
    e.status !== 429
  ) {
    uni.removeStorageSync("campus-pending-submit");
    pending.value = undefined;
    await load();
  }
}
async function submit() {
  if (!quote.value || busy.value) return;
  busy.value = true;
  if (
    !(await confirm(
      "订单合计 ¥" + quote.value.total + "。支付为模拟，无真实资金交易。",
    ))
  ) {
    busy.value = false;
    return;
  }
  const request = {
    userId: store.session?.id,
    key: uuid(),
    body: {
      addressId: addresses.value[index.value]!.id,
      cartVersion: quote.value.cartVersion,
      quoteHash: quote.value.quoteHash,
      remark: remark.value,
    } satisfies SubmitRequest,
  };
  pending.value = request;
  uni.setStorageSync("campus-pending-submit", request);
  try {
    await finish(
      await api<Order>("/orders", "POST", request.body, {
        "Idempotency-Key": request.key,
      }),
    );
  } catch (e) {
    await handleError(e);
  } finally {
    busy.value = false;
  }
}
onShow(load);

const navTo = (url: string) => uni.navigateTo({ url });
const tabTo = (url: string) => uni.switchTab({ url });
</script>
<template>
  <view class="screen"
    ><view class="section-title" style="margin-top: 0">确认这份校园餐</view
    ><view v-if="pending" class="card"
      ><view class="card-name">上次提交结果待确认</view
      ><view class="muted" style="margin: 20rpx 0"
        >先查询或使用原请求重试，避免重复下单。</view
      ><button
        class="primary"
        :loading="busy"
        :disabled="busy"
        @click="recover"
      >
        查询并恢复订单
      </button></view
    ><template v-else
      ><view class="card"
        ><view class="row"
          ><view class="card-name">送到哪里</view
          ><text class="tap" @click="navTo('/pages/addresses/index')"
            >管理地址</text
          ></view
        ><picker
          v-if="addresses.length"
          :range="
            addresses.map(
              (a) => (a.buildingName || '地址待完善') + ' ' + (a.room || ''),
            )
          "
          :value="index"
          @change="
            index = Number($event.detail.value);
            preview();
          "
          ><view class="line"
            >{{ addresses[index]?.buildingName }}
            {{ addresses[index]?.room }}　⌄</view
          ></picker
        ><view v-else class="muted" style="margin-top: 20rpx"
          >先新增一个校园地址。</view
        ><view class="muted"
          >{{ addresses[index]?.consignee }} {{ addresses[index]?.phone }}</view
        ></view
      ><view class="card" v-if="quote"
        ><view class="card-name">餐品与费用</view
        ><view class="line row" v-for="item in quote.items" :key="item.id"
          ><view>{{ item.name }} ×{{ item.quantity }}</view
          ><text
            >¥{{ (Number(item.price) * item.quantity).toFixed(2) }}</text
          ></view
        ><view class="line row"
          ><text>配送费</text><text>¥{{ quote.deliveryFee }}</text></view
        ><view class="line row"
          ><text>包装费</text><text>¥{{ quote.packagingFee }}</text></view
        ><view class="row" style="margin-top: 25rpx"
          ><text>合计</text><text class="price">¥{{ quote.total }}</text></view
        ></view
      ><view class="card"
        ><view class="field-label" style="margin-top: 0">备注</view
        ><input
          class="field"
          v-model="remark"
          maxlength="100"
          placeholder="可填写用餐备注" /></view
      ><view class="notice"
        >下单后预占供餐份数。15分钟内未支付自动取消；接单前取消返还份数，接单后不自动返还。</view
      ><button
        class="primary"
        :loading="busy"
        :disabled="!quote || busy"
        @click="submit"
      >
        提交订单 · 稍后模拟支付
      </button></template
    ></view
  >
</template>

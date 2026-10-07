<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { api, toast, loggedIn, image } from "../../api";
import { useSession } from "../../stores/session";
import type {
  Category,
  Product,
  Shop,
  Cart,
} from "../../../../contracts/models";
const store = useSession(),
  shop = ref<Shop>(),
  categories = ref<Category[]>([]),
  selected = ref(""),
  items = ref<Product[]>([]),
  cart = ref<Cart>({ cartVersion: "0", items: [] }),
  chosen = ref<Product>(),
  flavors = ref<Record<string, string>>({}),
  busy = ref(false);
async function load() {
  try {
    [shop.value, categories.value, cart.value] = await Promise.all([
      api<Shop>("/shop"),
      api<Category[]>("/menu/categories"),
      store.session ? api<Cart>("/cart") : Promise.resolve({cartVersion:"0",items:[]}),
    ]);
    if (!selected.value && categories.value[0])
      selected.value = categories.value[0].id;
    if (selected.value) await menu();
  } catch (e) {
    toast(e);
  }
}
async function menu() {
  const c = categories.value.find((x) => x.id === selected.value);
  if (!c) return;
  try {
    items.value = await api<Product[]>(
      "/menu/items?categoryId=" +
        c.id +
        "&type=" +
        (c.type === 1 ? "DISH" : "SETMEAL"),
    );
  } catch (e) {
    toast(e);
  }
}
function pick(item: Product) {
  if (!loggedIn()) return;
  if (!shop.value?.accepting) {
    toast(new Error("当前暂停接收新订单"));
    return;
  }
  if (!item.remaining) return;
  chosen.value = item;
  flavors.value = {};
  for (const f of item.flavors || []) flavors.value[f.name] = f.values[0] || "";
}
async function add() {
  if (!chosen.value || busy.value) return;
  busy.value = true;
  try {
    cart.value = await api<Cart>("/cart/items", "POST", {
      cartVersion: cart.value.cartVersion,
      itemType: chosen.value.itemType,
      itemId: chosen.value.id,
      flavors: flavors.value,
      delta: 1,
    });
    chosen.value = undefined;
    uni.showToast({ title: "已加入购物车", icon: "success" });
  } catch (e) {
    toast(e);
    cart.value = await api<Cart>("/cart");
  } finally {
    busy.value = false;
  }
}
function phone() {
  if (shop.value?.phone) uni.makePhoneCall({ phoneNumber: shop.value.phone });
}
onShow(load);

const navTo = (url: string) => uni.navigateTo({ url });
const tabTo = (url: string) => uni.switchTab({ url });
</script>
<template>
  <view class="screen"
    ><view class="row" style="margin-bottom: 25rpx"
      ><view
        ><view class="eyebrow">CAMPUS EATS</view
        ><view style="font-weight: 650; margin-top: 8rpx">校园点餐</view></view
      ><text class="tap" @click="navTo('/pages/login/index')">{{
        store.session?.name || "登录 / 切换账户"
      }}</text></view
    ><view class="hero"
      ><view class="row"
        ><view class="tag" :class="{ gray: !shop?.accepting }">{{
          shop?.accepting ? "正在营业" : "暂时休息"
        }}</view
        ><text class="muted">单校区配送</text></view
      ><text
        v-if="shop?.phone"
        class="tap"
        @click="phone"
        style="display: block; margin-top: 15rpx"
        >联系商家 {{ shop.phone }}</text
      ><view class="title">今天，也好好吃饭。</view
      ><view class="subtitle"
        >每日限量供餐，送到校园楼栋。<br />营业时段
        {{ shop?.hours?.join(" / ") }}</view
      ></view
    ><view class="notice" v-if="!shop?.accepting"
      >当前不接收新订单。可浏览菜单，或等待营业时段。</view
    ><scroll-view scroll-x style="white-space: nowrap; margin-bottom: 25rpx"
      ><text
        v-for="c in categories"
        :key="c.id"
        class="option"
        :class="{ selected: selected === c.id }"
        style="display: inline-block; margin-right: 15rpx"
        @click="
          selected = c.id;
          menu();
        "
        >{{ c.name }}</text
      ></scroll-view
    ><view class="card row" v-for="item in items" :key="item.id"
      ><view class="food-art"
        ><image
          v-if="item.image && !item.image.includes('dish.svg')"
          :src="image(item.image)"
          mode="aspectFill"
        /><text v-else>🍱</text></view
      ><view style="flex: 1"
        ><view class="card-name">{{ item.name }}</view
        ><view class="card-description">{{
          item.description || "现做校园餐，热乎送达"
        }}</view
        ><view class="muted" style="font-size: 20rpx; margin: 8rpx 0">{{
          item.remaining ? "剩余 " + item.remaining + " 份" : "今日已售罄"
        }}</view
        ><view class="row"
          ><text class="price">¥{{ item.price }}</text
          ><button
            class="primary small-button"
            :disabled="!item.remaining || !shop?.accepting"
            @click="pick(item)"
          >
            选餐
          </button></view
        ></view
      ></view
    ><view v-if="!items.length" class="empty"
      ><view class="empty-title">这里暂时没有餐品</view
      ><view>请选择其他分类，或稍后再来。</view></view
    ><view v-if="cart.items.length" class="sticky-summary row"
      ><view
        >{{ cart.items.reduce((n, x) => n + x.quantity, 0) }} 件已选餐品</view
      ><button class="primary small-button" @click="tabTo('/pages/cart/index')">
        查看购物车
      </button></view
    ><view class="modal" v-if="chosen" @click="chosen = undefined"
      ><view class="card" @click.stop
        ><view class="row"
          ><view class="section-title" style="margin: 0">{{ chosen.name }}</view
          ><text class="tap" @click="chosen = undefined">关闭</text></view
        ><view v-for="f in chosen.flavors || []" :key="f.name"
          ><view class="field-label">{{ f.name }}</view
          ><view class="options"
            ><text
              class="option"
              v-for="value in f.values"
              :key="value"
              :class="{ selected: flavors[f.name] === value }"
              @click="flavors[f.name] = value"
              >{{ value }}</text
            ></view
          ></view
        ><view
          v-if="chosen.components?.length"
          class="muted"
          style="margin: 25rpx 0"
          >套餐包含：{{
            chosen.components
              .map((c) => (c.currentName || c.name) + " ×" + c.copies)
              .join("、")
          }}</view
        ><view class="form-actions"
          ><button
            class="primary"
            :loading="busy"
            :disabled="busy"
            @click="add"
          >
            加入购物车 · ¥{{ chosen.price }}
          </button></view
        ></view
      ></view
    ></view
  >
</template>
